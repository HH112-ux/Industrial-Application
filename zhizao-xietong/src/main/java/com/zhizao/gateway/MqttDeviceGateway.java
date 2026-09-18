package com.zhizao.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.BusinessException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.service.DeviceDataService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 真实设备网关：订阅 MQTT 主题接收设备上报，解析后写入设备状态与遥测缓存。
 */
@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttDeviceGateway {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceGateway.class);

    private final DeviceDataService deviceDataService;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;
    @Value("${mqtt.username:}")
    private String username;
    @Value("${mqtt.password:}")
    private String password;
    @Value("${mqtt.topic:factory/devices/+/status}")
    private String topic;

    private MqttClient client;

    public MqttDeviceGateway(DeviceDataService deviceDataService, ObjectMapper objectMapper) {
        this.deviceDataService = deviceDataService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        new Thread(this::connectWithRetry, "mqtt-connect").start();
    }

    private void connectWithRetry() {
        int attempts = 0;
        while (attempts < 10) {
            try {
                String clientId = "zhizao-backend-" + UUID.randomUUID().toString().substring(0, 8);
                client = new MqttClient(brokerUrl, clientId);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                if (StringUtils.hasText(username)) {
                    options.setUserName(username);
                    options.setPassword(password.toCharArray());
                }
                client.connect(options);
                client.subscribe(topic, this::onMessage);
                log.info("MQTT 网关已连接 {}，订阅 {}", brokerUrl, topic);
                return;
            } catch (Exception e) {
                attempts++;
                log.warn("MQTT 连接失败（第 {} 次）：{}", attempts, e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("MQTT 网关连接失败，已放弃");
    }

    private void onMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
            DeviceGatewayReport report = objectMapper.readValue(payload, DeviceGatewayReport.class);
            deviceDataService.handleGatewayReport(report);
            log.info("MQTT 收到设备上报：{}", payload);
        } catch (Exception e) {
            log.warn("MQTT 消息解析失败：{}", new String(message.getPayload(), StandardCharsets.UTF_8), e);
        }
    }

    /** 发布一条测试上报（演示设备数据上行链路）。 */
    public void publishTest(DeviceGatewayReport report) {
        if (client == null || !client.isConnected()) {
            throw new BusinessException("MQTT 网关未连接");
        }
        try {
            String payload = objectMapper.writeValueAsString(report);
            String targetTopic = "factory/devices/" + report.deviceNo() + "/status";
            client.publish(targetTopic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException("MQTT 发布失败：" + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception ignored) {
            }
        }
    }
}
