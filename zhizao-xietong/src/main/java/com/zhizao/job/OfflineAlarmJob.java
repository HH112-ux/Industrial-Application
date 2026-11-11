package com.zhizao.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AlarmRule;
import com.zhizao.entity.Device;
import com.zhizao.enums.AlarmStatus;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.AlarmRuleMapper;
import com.zhizao.mapper.DeviceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 离线报警任务：按在线阈值扫描启用了离线规则的设备，未恢复期间不重复建单。
 *
 * <p>单台设备判定失败只记 WARN，继续扫描其余设备。设备重新上报后由遥测链路的
 * {@link com.zhizao.service.AlarmEvaluationService#recoverOffline(Long)} 自动恢复。</p>
 *
 * @author Caspian
 */
@Component
public class OfflineAlarmJob {

    private static final Logger log = LoggerFactory.getLogger(OfflineAlarmJob.class);
    private static final String OFFLINE = "OFFLINE";

    private final AlarmRuleMapper ruleMapper;
    private final AlarmRecordMapper recordMapper;
    private final DeviceMapper deviceMapper;

    @Value("${device.online-threshold-seconds:90}")
    private long onlineThresholdSeconds = 90;

    /**
     * 构造离线扫描任务。
     *
     * @param ruleMapper   报警规则数据访问
     * @param recordMapper 报警记录数据访问
     * @param deviceMapper 设备数据访问
     */
    public OfflineAlarmJob(AlarmRuleMapper ruleMapper, AlarmRecordMapper recordMapper,
                           DeviceMapper deviceMapper) {
        this.ruleMapper = ruleMapper;
        this.recordMapper = recordMapper;
        this.deviceMapper = deviceMapper;
    }

    /** 按系统时间扫描，供调度器周期触发。 */
    @Scheduled(fixedDelayString = "${device.alarm.offline-scan-ms:60000}",
            initialDelayString = "${device.alarm.offline-scan-ms:60000}")
    public void scan() {
        scan(Clock.systemDefaultZone());
    }

    /**
     * 按指定时钟扫描离线设备。
     *
     * @param clock 计算当前时间所用的时钟，便于测试注入
     * @return 本次新开的报警数量
     */
    public int scan(Clock clock) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minusSeconds(onlineThresholdSeconds);
        List<AlarmRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<AlarmRule>()
                .eq(AlarmRule::getRuleType, OFFLINE)
                .eq(AlarmRule::getEnabled, Boolean.TRUE));
        int opened = 0;
        for (AlarmRule rule : rules) {
            try {
                if (evaluate(rule, cutoff, now)) {
                    opened++;
                }
            } catch (RuntimeException e) {
                log.warn("离线判定失败，已忽略：deviceId={}，原因={}", rule.getDeviceId(), e.getMessage());
            }
        }
        return opened;
    }

    private boolean evaluate(AlarmRule rule, LocalDateTime cutoff, LocalDateTime now) {
        Device device = deviceMapper.selectById(rule.getDeviceId());
        if (device == null) {
            return false;
        }
        LocalDateTime lastSeen = device.getLastSeenAt();
        if (lastSeen != null && lastSeen.isAfter(cutoff)) {
            return false;
        }
        Long active = recordMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getDeviceId, rule.getDeviceId())
                .eq(AlarmRecord::getRuleType, OFFLINE)
                .eq(AlarmRecord::getMetric, "")
                .eq(AlarmRecord::getActiveFlag, 1));
        if (active != null && active > 0) {
            return false;
        }
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(rule.getDeviceId());
        record.setMetric("");
        record.setRuleType(OFFLINE);
        record.setThresholdSnapshot("offlineSeconds=" + onlineThresholdSeconds
                + ", lastSeenAt=" + (lastSeen == null ? "-" : lastSeen));
        record.setStartTime(now);
        record.setStatus(AlarmStatus.OPEN.name());
        record.setActiveFlag(1);
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return false;
        }
        return true;
    }
}
