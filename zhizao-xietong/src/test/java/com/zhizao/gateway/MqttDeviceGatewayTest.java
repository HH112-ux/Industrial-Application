package com.zhizao.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
 * MQTT 网关测试：重连退避、连接/订阅时序、会话恢复与 ACL 覆盖检查。
 *
 * @author Caspian
 */
@ExtendWith(OutputCaptureExtension.class)
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
    void aclWildcardCoverageMatchesMqttRules() {
        assertTrue(MqttDeviceGateway.aclCovers(TOPIC, TOPIC));
        assertTrue(MqttDeviceGateway.aclCovers("factory/devices/+/status", TOPIC));
        assertTrue(MqttDeviceGateway.aclCovers("factory/#", TOPIC));
        assertFalse(MqttDeviceGateway.aclCovers("factory/devices/+/telemetry", TOPIC));
        assertFalse(MqttDeviceGateway.aclCovers("factory/devices/+", TOPIC));
        assertFalse(MqttDeviceGateway.aclCovers("factory/#/status", TOPIC));
    }

    @Test
    void aclTopicsSplitOnWhitespaceOnlyToMatchMqttInit() {
        assertEquals(List.of("factory/devices/+/status", "factory/+/telemetry"),
                MqttDeviceGateway.parseAclTopics(" factory/devices/+/status   factory/+/telemetry "));
        assertEquals(List.of("factory/devices/+/status,factory/+/telemetry"),
                MqttDeviceGateway.parseAclTopics("factory/devices/+/status,factory/+/telemetry"),
                "逗号不是分隔符：与 mqtt-init 的 shell 拆分保持一致，避免掩盖非法 ACL");
        assertTrue(MqttDeviceGateway.parseAclTopics("").isEmpty());
        assertTrue(MqttDeviceGateway.parseAclTopics(null).isEmpty());
    }

    @Test
    void warnsWhenSubscriptionTopicIsOutsideAcl(CapturedOutput output) {
        ReflectionTestUtils.setField(gateway, "aclTopics", "factory/devices/+/telemetry");

        gateway.logStartupConfig();

        assertTrue(output.getOut().contains("不在 MQTT_ACL_TOPICS"),
                "订阅主题与 ACL 分叉时必须给出启动告警，避免遥测静默断流");
    }

    @Test
    void noWarningWhenAclCoversSubscriptionTopic(CapturedOutput output) {
        ReflectionTestUtils.setField(gateway, "aclTopics", "factory/devices/+/telemetry factory/#");

        gateway.logStartupConfig();

        assertFalse(output.getOut().contains("不在 MQTT_ACL_TOPICS"));
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
