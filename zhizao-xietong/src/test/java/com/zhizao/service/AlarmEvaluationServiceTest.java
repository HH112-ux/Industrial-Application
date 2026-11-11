package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AlarmRule;
import com.zhizao.mapper.AlarmRecordMapper;
import com.zhizao.mapper.AlarmRuleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 阈值判定测试：抖动累积、开记录去重、恢复闭环与插入竞态。 */
@ExtendWith(MockitoExtension.class)
class AlarmEvaluationServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlarmRule.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlarmRecord.class);
    }

    @Mock
    private AlarmRuleMapper ruleMapper;
    @Mock
    private AlarmRecordMapper recordMapper;

    private AlarmEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new AlarmEvaluationService(ruleMapper, recordMapper);
    }

    private AlarmRule rule(int consecutiveTimes, int streak) {
        AlarmRule rule = new AlarmRule();
        rule.setId(5L);
        rule.setDeviceId(1L);
        rule.setMetric("temperature");
        rule.setRuleType("THRESHOLD");
        rule.setUpperLimit(80.0);
        rule.setConsecutiveTimes(consecutiveTimes);
        rule.setCurrentStreak(streak);
        return rule;
    }

    private DeviceGatewayReport report(Double temperature) {
        return new DeviceGatewayReport("EQ-202609-0001", "RUNNING", temperature, 1500.0, 8.5);
    }

    private AlarmRecord activeRecord() {
        AlarmRecord record = new AlarmRecord();
        record.setId(77L);
        return record;
    }

    private LambdaUpdateWrapper<AlarmRecord> captureUpdate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<AlarmRecord>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(recordMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    @Test
    void breachBelowConsecutiveTimesOnlyAccumulatesStreak() {
        AlarmRule rule = rule(3, 1);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recordMapper.selectOne(any())).thenReturn(null);

        service.evaluateThreshold(1L, report(90.0));
        service.evaluateThreshold(1L, report(null));

        assertEquals(2, rule.getCurrentStreak());
        verify(ruleMapper).updateById(rule);
        verify(recordMapper, never()).insert(any(AlarmRecord.class));
    }

    @Test
    void reachingConsecutiveTimesOpensOnceThenRefreshes() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(3, 2)));
        when(recordMapper.selectOne(any())).thenReturn(null, activeRecord());

        service.evaluateThreshold(1L, report(90.0));
        service.evaluateThreshold(1L, report(95.0));

        ArgumentCaptor<AlarmRecord> captor = ArgumentCaptor.forClass(AlarmRecord.class);
        verify(recordMapper).insert(captor.capture());
        assertEquals("OPEN", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getActiveFlag());
        assertEquals(90.0, captor.getValue().getTriggerValue(), 0.01);
        assertEquals("upper=80.0, lower=-", captor.getValue().getThresholdSnapshot());
        assertTrue(captureUpdate().getSqlSet().contains("trigger_value"));
    }

    @Test
    void recoverOfflineClosesActiveOfflineRecord() {
        when(recordMapper.selectOne(any())).thenReturn(activeRecord());

        service.recoverOffline(1L);

        String sqlSet = captureUpdate().getSqlSet();
        assertTrue(sqlSet.contains("recover_time"));
        assertTrue(sqlSet.contains("active_flag = NULL"));
    }

    @Test
    void recoverOfflineIgnoresWhenNoActiveRecord() {
        when(recordMapper.selectOne(any())).thenReturn(null);

        service.recoverOffline(1L);

        verify(recordMapper, never()).update(isNull(), any());
    }

    @Test
    void normalValueRecoversAlarmAndResetsStreak() {
        AlarmRule rule = rule(3, 4);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(recordMapper.selectOne(any())).thenReturn(activeRecord());

        service.evaluateThreshold(1L, report(60.0));

        assertEquals(0, rule.getCurrentStreak());
        String sqlSet = captureUpdate().getSqlSet();
        assertTrue(sqlSet.contains("recover_time"));
        assertTrue(sqlSet.contains("active_flag = NULL"));
    }

    @Test
    void duplicateInsertFallsBackToRefresh() {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule(3, 2)));
        when(recordMapper.selectOne(any())).thenReturn(null, activeRecord());
        when(recordMapper.insert(any(AlarmRecord.class))).thenThrow(new DuplicateKeyException("uk_alarm_active"));

        service.evaluateThreshold(1L, report(90.0));

        assertTrue(captureUpdate().getSqlSet().contains("trigger_value"));
    }
}
