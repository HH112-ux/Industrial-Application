package com.zhizao.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.entity.AlarmRule;
import com.zhizao.entity.Device;
import com.zhizao.mapper.AlarmRuleMapper;
import com.zhizao.mapper.DeviceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 报警规则服务测试：参数校验、默认值与唯一性校验。
 *
 * @author Caspian
 */
@ExtendWith(MockitoExtension.class)
class AlarmRuleServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlarmRule.class);
    }

    @Mock
    private AlarmRuleMapper ruleMapper;
    @Mock
    private DeviceMapper deviceMapper;

    private AlarmRuleService service() {
        return new AlarmRuleService(ruleMapper, deviceMapper);
    }

    @BeforeEach
    void deviceExists() {
        Device device = new Device();
        device.setId(1L);
        lenient().when(deviceMapper.selectById(1L)).thenReturn(device);
    }

    private AlarmRule thresholdRule() {
        AlarmRule rule = new AlarmRule();
        rule.setDeviceId(1L);
        rule.setRuleType("THRESHOLD");
        rule.setMetric("temperature");
        rule.setUpperLimit(80.0);
        return rule;
    }
    @Test
    void createDefaultsConsecutiveTimesToThree() {
        when(ruleMapper.selectCount(any())).thenReturn(0L);
        AlarmRule saved = service().create(thresholdRule());
        assertEquals(3, saved.getConsecutiveTimes());
        assertTrue(saved.getEnabled());
        assertEquals(0, saved.getCurrentStreak());
        verify(ruleMapper).insert(saved);
    }
    @Test
    void createRequiresAtLeastOneLimit() {
        AlarmRule rule = thresholdRule();
        rule.setUpperLimit(null);
        BadRequestException error = assertThrows(BadRequestException.class, () -> service().create(rule));
        assertEquals("阈值规则至少需要配置上限或下限", error.getMessage());
        verify(ruleMapper, never()).insert(any(AlarmRule.class));
    }
    @Test
    void createRejectsInvalidFields() {
        AlarmRule reversed = thresholdRule();
        reversed.setLowerLimit(80.0);
        AlarmRule unknownMetric = thresholdRule();
        unknownMetric.setMetric("pressure");
        AlarmRule offline = thresholdRule();
        offline.setRuleType("OFFLINE");
        assertAll(
                () -> assertThrows(BadRequestException.class, () -> service().create(reversed)),
                () -> assertThrows(BadRequestException.class, () -> service().create(unknownMetric)),
                () -> assertThrows(BadRequestException.class, () -> service().create(offline)));
    }
    @Test
    void duplicateRuleRejectedBeforeInsert() {
        when(ruleMapper.selectCount(any())).thenReturn(1L);
        BusinessException error = assertThrows(BusinessException.class, () -> service().create(thresholdRule()));
        assertEquals(409, error.getCode());
        verify(ruleMapper, never()).insert(any(AlarmRule.class));
    }
    @Test
    void updateKeepsDeviceAndEnabledFromExisting() {
        AlarmRule existing = new AlarmRule();
        existing.setId(5L);
        existing.setDeviceId(1L);
        existing.setEnabled(false);
        existing.setConsecutiveTimes(7);
        AlarmRule reloaded = thresholdRule();
        reloaded.setId(5L);
        when(ruleMapper.selectById(5L)).thenReturn(existing, reloaded);
        when(ruleMapper.selectCount(any())).thenReturn(0L);
        AlarmRule input = thresholdRule();
        input.setDeviceId(99L);
        service().update(5L, input);
        ArgumentCaptor<AlarmRule> captor = ArgumentCaptor.forClass(AlarmRule.class);
        verify(ruleMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getDeviceId());
        assertFalse(captor.getValue().getEnabled());
        assertEquals(7, captor.getValue().getConsecutiveTimes());
    }
    @Test
    void updateRejectsDuplicateCombo() {
        AlarmRule existing = new AlarmRule();
        existing.setId(5L);
        existing.setDeviceId(1L);
        when(ruleMapper.selectById(5L)).thenReturn(existing);
        when(ruleMapper.selectCount(any())).thenReturn(1L);
        BusinessException error = assertThrows(BusinessException.class, () -> service().update(5L, thresholdRule()));
        assertEquals(409, error.getCode());
        verify(ruleMapper, never()).updateById(any(AlarmRule.class));
    }
    @Test
    void deleteMissingRuleThrows() {
        when(ruleMapper.selectById(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service().delete(9L));
        verify(ruleMapper, never()).deleteById(9L);
    }
}
