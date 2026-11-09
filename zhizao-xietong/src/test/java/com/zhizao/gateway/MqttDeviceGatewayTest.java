package com.zhizao.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MQTT 网关测试：重连退避、连接/订阅时序与会话恢复。
 *
 * @author Caspian
 */
class MqttDeviceGatewayTest {

    private static final String TOPIC = "factory/devices/+/status";

    private TestGateway gateway;

    /**
     * 构造注入了固定配置的网关，避免依赖 Spring 环境。
     */
    @BeforeEach
    void setUp() {
        gateway = new TestGateway();
        ReflectionTestUtils.setField(gateway, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(gateway, "clientId", "test-client");
        ReflectionTestUtils.setField(gateway, "topic", TOPIC);
        ReflectionTestUtils.setField(gateway, "qos", 1);
    }

    @Test
    void retryDelayGrowsExponentiallyAndStopsAtOneMinute() {
        assertEquals(1_000L, MqttDeviceGateway.retryDelay(0));
        assertEquals(2_000L, MqttDeviceGateway.retryDelay(1));
        assertEquals(4_000L, MqttDeviceGateway.retryDelay(2));
        assertEquals(32_000L, MqttDeviceGateway.retryDelay(5));
        assertEquals(60_000L, MqttDeviceGateway.retryDelay(6));
        assertEquals(60_000L, MqttDeviceGateway.retryDelay(30));
    }

    @Test
    void clientIsAssignedBeforeConnectAndSubscriptionIsNotDropped() throws Exception {
        MqttClient client = mock(MqttClient.class);
        IMqttToken token = mock(IMqttToken.class);
        when(token.getSessionPresent()).thenReturn(false);
        when(client.isConnected()).thenReturn(true);
        when(client.connectWithResult(any(MqttConnectOptions.class))).thenAnswer(invocation -> {
            assertSame(client, ReflectionTestUtils.getField(gateway, "client"),
                    "发起 connect 前必须先把 client 赋值，否则 connectComplete 内订阅会静默丢弃");
            gateway.connectComplete(false, "tcp://localhost:1883");
            return token;
        });
        gateway.nextClient = client;

        gateway.connect();

        verify(client).subscribe(TOPIC, 1);
    }

    @Test
    void existingSessionSkipsResubscribe() throws Exception {
        MqttClient client = mock(MqttClient.class);
        IMqttToken token = mock(IMqttToken.class);
        when(token.getSessionPresent()).thenReturn(true);
        when(client.connectWithResult(any(MqttConnectOptions.class))).thenReturn(token);
        gateway.nextClient = client;

        gateway.connect();

        verify(client, never()).subscribe(TOPIC, 1);
    }

    @Test
    void subscribeWithMissingClientIsNotSilentlyDropped() {
        assertFalse(gateway.subscribe(null));
        assertTrue(gateway.reconnectScheduled, "客户端未就绪时应安排重连而不是静默返回");
    }

    @Test
    void replacingClientClosesPreviousInstance() throws Exception {
        MqttClient previous = mock(MqttClient.class);
        ReflectionTestUtils.setField(gateway, "client", previous);

        MqttClient fresh = mock(MqttClient.class);
        IMqttToken token = mock(IMqttToken.class);
        when(token.getSessionPresent()).thenReturn(true);
        when(fresh.connectWithResult(any(MqttConnectOptions.class))).thenReturn(token);
        gateway.nextClient = fresh;

        gateway.connect();

        verify(previous).disconnectForcibly(0, 0);
        verify(previous).close(true);
    }

    /**
     * 可替换客户端与重连调度的测试网关。
     */
    private static class TestGateway extends MqttDeviceGateway {

        private MqttClient nextClient;
        private boolean reconnectScheduled;

        TestGateway() {
            super(mock(DeviceDataService.class), new ObjectMapper(), mock(DeviceSignatureVerifier.class));
        }

        @Override
        MqttClient createClient() {
            return nextClient;
        }

        @Override
        void scheduleReconnect() {
            reconnectScheduled = true;
        }
    }
}
