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

    private final AlarmRuleMapper ruleMapper;
    private final AlarmRecordMapper recordMapper;

    public AlarmEvaluationService(AlarmRuleMapper ruleMapper, AlarmRecordMapper recordMapper) {
        this.ruleMapper = ruleMapper;
        this.recordMapper = recordMapper;
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

    private void handleBreach(AlarmRule rule, Double value, AlarmRecord active) {
        int streak = (rule.getCurrentStreak() == null ? 0 : rule.getCurrentStreak()) + 1;
        rule.setCurrentStreak(streak);
        ruleMapper.updateById(rule);
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
            rule.setCurrentStreak(0);
            ruleMapper.updateById(rule);
        }
        if (active != null) {
            recover(active.getId());
        }
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
