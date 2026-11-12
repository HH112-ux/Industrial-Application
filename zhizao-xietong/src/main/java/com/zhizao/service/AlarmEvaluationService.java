package com.zhizao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AlarmRule;
import com.zhizao.enums.AlarmStatus;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.AlarmRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 阈值判定：连续 N 次越界才开记录，未恢复期间只刷新触发值，值恢复后自动置已恢复。 */
@Service
public class AlarmEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluationService.class);
    private static final String THRESHOLD = "THRESHOLD";
    private static final String OFFLINE = "OFFLINE";

    private final AlarmRuleMapper ruleMapper;
    private final AlarmRecordMapper recordMapper;
    private final WeComAlarmNotifier notifier;

    public AlarmEvaluationService(AlarmRuleMapper ruleMapper, AlarmRecordMapper recordMapper,
                                  WeComAlarmNotifier notifier) {
        this.ruleMapper = ruleMapper;
        this.recordMapper = recordMapper;
        this.notifier = notifier;
    }

    public void evaluateThreshold(Long deviceId, DeviceGatewayReport report) {
        List<AlarmRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(AlarmRule::getDeviceId, deviceId)
                .eq(AlarmRule::getRuleType, THRESHOLD)
                .eq(AlarmRule::getEnabled, Boolean.TRUE));
        for (AlarmRule rule : rules) {
            Double value = metricValue(rule.getMetric(), report);
            if (value == null) {
                continue;
            }
            AlarmRecord active = findActive(rule);
            boolean breached = (rule.getUpperLimit() != null && value > rule.getUpperLimit())
                    || (rule.getLowerLimit() != null && value < rule.getLowerLimit());
            if (breached) {
                handleBreach(rule, value, active);
            } else {
                handleRecovered(rule, active);
            }
        }
    }

    /**
     * 设备重新上报后关闭在途的离线报警；没有在途记录时什么都不做。
     *
     * @param deviceId 设备主键
     */
    public void recoverOffline(Long deviceId) {
        AlarmRecord active = recordMapper.selectOne(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getDeviceId, deviceId)
                .eq(AlarmRecord::getRuleType, OFFLINE)
                .eq(AlarmRecord::getMetric, "")
                .eq(AlarmRecord::getActiveFlag, 1)
                .last("LIMIT 1"));
        if (active != null) {
            recover(active.getId());
        }
    }

    private void handleBreach(AlarmRule rule, Double value, AlarmRecord active) {
        int streak = incrementStreak(rule.getId());
        int required = rule.getConsecutiveTimes() == null || rule.getConsecutiveTimes() < 1
                ? 1 : rule.getConsecutiveTimes();
        if (active != null) {
            refresh(active.getId(), value);
        } else if (streak >= required) {
            open(rule, value);
        }
    }
    private void handleRecovered(AlarmRule rule, AlarmRecord active) {
        if (rule.getCurrentStreak() != null && rule.getCurrentStreak() != 0) {
            resetStreak(rule.getId());
        }
        if (active != null) {
            recover(active.getId());
        }
    }

    /**
     * 原子自增连续越界次数，只写 {@code current_streak} 一列。
     *
     * <p>不能读-改-写整实体：并发上报会互相覆盖计数，旧快照还会把管理员刚改的阈值配置写回去。
     * 自增后回读一次，拿到的值即使被其他上报再自增也只会偏大，不影响「达到次数即开单」的判定。</p>
     *
     * @param ruleId 规则主键
     * @return 自增后的次数；规则已被删除时返回 0
     */
    private int incrementStreak(Long ruleId) {
        int rows = ruleMapper.update(null, new LambdaUpdateWrapper<AlarmRule>()
                .eq(AlarmRule::getId, ruleId)
                .setSql("current_streak = current_streak + 1"));
        if (rows == 0) {
            return 0;
        }
        AlarmRule latest = ruleMapper.selectById(ruleId);
        return latest == null || latest.getCurrentStreak() == null ? 0 : latest.getCurrentStreak();
    }

    /**
     * 条件清零连续越界次数，已经是 0 时不产生实际写入。
     *
     * @param ruleId 规则主键
     */
    private void resetStreak(Long ruleId) {
        ruleMapper.update(null, new LambdaUpdateWrapper<AlarmRule>()
                .eq(AlarmRule::getId, ruleId)
                .ne(AlarmRule::getCurrentStreak, 0)
                .set(AlarmRule::getCurrentStreak, 0));
    }
    private Double metricValue(String metric, DeviceGatewayReport report) {
        if (metric == null) {
            return null;
        }
        return switch (metric) {
            case "temperature" -> report.temperature();
            case "rpm" -> report.rpm();
            case "current" -> report.current();
            default -> null;
        };
    }
    private AlarmRecord findActive(AlarmRule rule) {
        return recordMapper.selectOne(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getDeviceId, rule.getDeviceId())
                .eq(AlarmRecord::getRuleType, rule.getRuleType())
                .eq(AlarmRecord::getMetric, rule.getMetric())
                .eq(AlarmRecord::getActiveFlag, 1)
                .last("LIMIT 1"));
    }

    private void open(AlarmRule rule, Double value) {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(rule.getDeviceId());
        record.setMetric(rule.getMetric());
        record.setRuleType(rule.getRuleType());
        record.setTriggerValue(value);
        record.setThresholdSnapshot("upper=" + (rule.getUpperLimit() == null ? "-" : rule.getUpperLimit())
                + ", lower=" + (rule.getLowerLimit() == null ? "-" : rule.getLowerLimit()));
        record.setStartTime(LocalDateTime.now());
        record.setStatus(AlarmStatus.OPEN.name());
        record.setActiveFlag(1);
        try {
            recordMapper.insert(record);
            notifier.notify(record);
        } catch (DuplicateKeyException e) {
            AlarmRecord active = findActive(rule);
            if (active != null) {
                refresh(active.getId(), value);
            }
            log.debug("报警记录已存在，刷新触发值：deviceId={}，metric={}", rule.getDeviceId(), rule.getMetric());
        }
    }

    private void refresh(Long recordId, Double value) {
        recordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                .eq(AlarmRecord::getId, recordId)
                .set(AlarmRecord::getTriggerValue, value));
    }

    /** 恢复时 active_flag 必须显式置空，updateById 会忽略 null 字段。 */
    private void recover(Long recordId) {
        recordMapper.update(null, new LambdaUpdateWrapper<AlarmRecord>()
                .eq(AlarmRecord::getId, recordId)
                .set(AlarmRecord::getStatus, AlarmStatus.RECOVERED.name())
                .set(AlarmRecord::getRecoverTime, LocalDateTime.now())
                .setSql("active_flag = NULL"));
    }
}
