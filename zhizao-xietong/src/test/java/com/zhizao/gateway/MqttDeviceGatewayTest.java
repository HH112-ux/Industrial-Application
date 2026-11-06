package com.zhizao.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MQTT 网关重连策略测试：验证指数退避的上下界。
 *
 * @author Caspian
 */
class MqttDeviceGatewayTest {

    @Test
    void retryDelayGrowsExponentiallyAndStopsAtOneMinute() {
        assertEquals(1_000L, MqttDeviceGateway.retryDelay(0));
        assertEquals(2_000L, MqttDeviceGateway.retryDelay(1));
        assertEquals(4_000L, MqttDeviceGateway.retryDelay(2));
        assertEquals(32_000L, MqttDeviceGateway.retryDelay(5));
        assertEquals(60_000L, MqttDeviceGateway.retryDelay(6));
        assertEquals(60_000L, MqttDeviceGateway.retryDelay(30));
    }
}
