package com.zhizao.service;

import com.zhizao.entity.AlarmRecord;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 企微通知测试：开关关闭或配置缺失时静默跳过，不抛异常。 */
class WeComAlarmNotifierTest {

    private AlarmRecord record() {
        AlarmRecord record = new AlarmRecord();
        record.setDeviceId(1L);
        record.setRuleType("THRESHOLD");
        record.setMetric("temperature");
        record.setTriggerValue(90.0);
        record.setThresholdSnapshot("upper=80.0, lower=-");
        return record;
    }

    @Test
    void disabledSwitchSkipsSilently() {
        WeComAlarmNotifier notifier = new WeComAlarmNotifier(RestClient.builder());
        assertDoesNotThrow(() -> notifier.notify(record()));
    }

    @Test
    void enabledWithoutConfigurationSkipsSilently() {
        WeComAlarmNotifier notifier = new WeComAlarmNotifier(RestClient.builder());
        ReflectionTestUtils.setField(notifier, "enabled", true);
        assertDoesNotThrow(() -> notifier.notify(record()));
    }
}
