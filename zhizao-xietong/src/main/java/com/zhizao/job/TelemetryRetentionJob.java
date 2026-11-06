package com.zhizao.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.DeviceTelemetryRecord;
import com.zhizao.mapper.DeviceTelemetryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 遥测保留任务：每天清理超过保留天数的时序数据，避免单表无限增长。
 *
 * @author Caspian
 */
@Component
public class TelemetryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

    private final DeviceTelemetryMapper telemetryMapper;

    @Value("${device.telemetry.retention-days:90}")
    private int retentionDays = 90;

    /**
     * 构造保留任务。
     *
     * @param telemetryMapper 遥测时序数据访问
     */
    public TelemetryRetentionJob(DeviceTelemetryMapper telemetryMapper) {
        this.telemetryMapper = telemetryMapper;
    }

    /**
     * 按系统时间执行清理，供调度器每天触发。
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        cleanup(Clock.systemDefaultZone());
    }

    /**
     * 按指定时钟清理过期遥测，便于测试注入固定时间。
     *
     * @param clock 计算截止时间所用的时钟
     * @return 本次删除的行数
     */
    public int cleanup(Clock clock) {
        LocalDateTime cutoff = cutoff(clock);
        int deleted = telemetryMapper.delete(
                new LambdaQueryWrapper<DeviceTelemetryRecord>()
                        .lt(DeviceTelemetryRecord::getReportedAt, cutoff));
        if (deleted > 0) {
            log.info("已清理 {} 条早于 {} 的设备遥测", deleted, cutoff);
        }
        return deleted;
    }

    /**
     * 计算保留截止时间，早于该时间的记录会被清理。
     *
     * @param clock 计算所用的时钟
     * @return 当前时间减去保留天数
     */
    LocalDateTime cutoff(Clock clock) {
        return LocalDateTime.now(clock).minusDays(retentionDays);
    }
}
