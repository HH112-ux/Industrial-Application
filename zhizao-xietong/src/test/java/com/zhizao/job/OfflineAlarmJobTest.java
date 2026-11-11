package com.zhizao.job;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AlarmRule;
import com.zhizao.entity.Device;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.AlarmRuleMapper;
import com.zhizao.mapper.DeviceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 离线判定测试：超时开单、在线跳过、重复扫描去重与插入竞态。 */
@ExtendWith(MockitoExtension.class)
class OfflineAlarmJobTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlarmRule.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlarmRecord.class);
    }

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private AlarmRuleMapper ruleMapper;
    @Mock
    private AlarmRecordMapper recordMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private OfflineAlarmJob job;

    @BeforeEach
    void setUp() {
        job = new OfflineAlarmJob(ruleMapper, recordMapper, deviceMapper);
    }

    private AlarmRule offlineRule() {
        AlarmRule rule = new AlarmRule();
        rule.setId(3L);
        rule.setDeviceId(1L);
        rule.setRuleType("OFFLINE");
        rule.setMetric("");
        return rule;
    }

    private Device device(LocalDateTime lastSeenAt) {
        Device device = new Device();
        device.setId(1L);
        device.setLastSeenAt(lastSeenAt);
        return device;
    }

    @Test
    void offlineDeviceOpensRecord() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(offlineRule()));
        when(deviceMapper.selectById(1L))
                .thenReturn(device(LocalDateTime.now(CLOCK).minusMinutes(5)));
        when(recordMapper.selectCount(any())).thenReturn(0L);

        assertEquals(1, job.scan(CLOCK));

        ArgumentCaptor<AlarmRecord> captor = ArgumentCaptor.forClass(AlarmRecord.class);
        verify(recordMapper).insert(captor.capture());
        AlarmRecord saved = captor.getValue();
        assertEquals("OFFLINE", saved.getRuleType());
        assertEquals("", saved.getMetric());
        assertEquals("OPEN", saved.getStatus());
        assertEquals(1, saved.getActiveFlag());
        assertTrue(saved.getThresholdSnapshot().contains("offlineSeconds=90"));
    }

    @Test
    void neverSeenDeviceIsTreatedAsOffline() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(offlineRule()));
        when(deviceMapper.selectById(1L)).thenReturn(device(null));
        when(recordMapper.selectCount(any())).thenReturn(0L);

        assertEquals(1, job.scan(CLOCK));
        verify(recordMapper).insert(any(AlarmRecord.class));
    }

    @Test
    void onlineDeviceIsSkipped() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(offlineRule()));
        when(deviceMapper.selectById(1L))
                .thenReturn(device(LocalDateTime.now(CLOCK).minusSeconds(30)));

        assertEquals(0, job.scan(CLOCK));
        verify(recordMapper, never()).insert(any(AlarmRecord.class));
    }

    @Test
    void activeRecordIsNotDuplicated() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(offlineRule()));
        when(deviceMapper.selectById(1L))
                .thenReturn(device(LocalDateTime.now(CLOCK).minusMinutes(10)));
        when(recordMapper.selectCount(any())).thenReturn(1L);

        assertEquals(0, job.scan(CLOCK));
        verify(recordMapper, never()).insert(any(AlarmRecord.class));
    }

    @Test
    void duplicateInsertIsSwallowed() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(offlineRule()));
        when(deviceMapper.selectById(1L))
                .thenReturn(device(LocalDateTime.now(CLOCK).minusMinutes(10)));
        when(recordMapper.selectCount(any())).thenReturn(0L);
        when(recordMapper.insert(any(AlarmRecord.class))).thenThrow(new DuplicateKeyException("uk_alarm_active"));

        assertEquals(0, job.scan(CLOCK));
    }
}
