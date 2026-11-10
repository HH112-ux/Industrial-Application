package com.zhizao.job;

import com.zhizao.entity.DeviceTelemetryRecord;
import com.zhizao.mapper.DeviceTelemetryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 遥测保留任务测试：截止时间计算与删除行数返回。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class TelemetryRetentionJobTest {

    @Mock
    private DeviceTelemetryMapper telemetryMapper;

    @Test
    void cutoffIsRetentionDaysBeforeCurrentTime() {
        TelemetryRetentionJob job = new TelemetryRetentionJob(telemetryMapper);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);

        assertEquals(LocalDateTime.of(2026, 8, 8, 0, 0), job.cutoff(clock));
    }

    @Test
    void cleanupDeletesAndReturnsAffectedRows() {
        TelemetryRetentionJob job = new TelemetryRetentionJob(telemetryMapper);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
        when(telemetryMapper.delete(any())).thenReturn(3);

        assertEquals(3, job.cleanup(clock));
        verify(telemetryMapper).delete(any());
    }

    @Test
    void cleanupDeletesInBatchesUntilBelowBatchSize() {
        TelemetryRetentionJob job = new TelemetryRetentionJob(telemetryMapper);
        Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
        when(telemetryMapper.delete(any())).thenReturn(5000, 1);

        assertEquals(5001, job.cleanup(clock));
        verify(telemetryMapper, times(2)).delete(any());
    }
}
