package com.zhizao.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 真实设备网关：订阅 MQTT 主题接收设备上报，解析后更新设备状态与遥测。
 *
 * <p>重连策略：持久会话（cleanSession=false）保留订阅；连接丢失后由 Paho 自动重连，
 * 重连成功回调内再次订阅；首次连接失败按指数退避重试，最长间隔 60 秒。
 * 多实例部署时 `mqtt.client-id` 必须每实例唯一，否则会互相顶掉会话。</p>
 *
 * @author Caspian
 */
@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttDeviceGateway implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceGateway.class);
    private static final long INITIAL_RETRY_DELAY_MS = 1_000L;
    private static final long MAX_RETRY_DELAY_MS = 60_000L;
    private static final int MAX_BACKOFF_SHIFT = 6;

    private final DeviceDataService deviceDataService;
    private final ObjectMapper objectMapper;
    private final DeviceSignatureVerifier signatureVerifier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-gateway");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;
    @Value("${mqtt.username:}")
    private String username;
    @Value("${mqtt.password:}")
    private String password;
    @Value("${mqtt.topic:factory/devices/+/status}")
    private String topic;
    @Value("${mqtt.client-id:zhizao-backend}")
    private String clientId;
    @Value("${mqtt.qos:1}")
    private int qos;

    private volatile MqttClient client;
    private volatile boolean running = true;
    private int reconnectAttempt;

    /**
     * 构造 MQTT 设备网关。
     *
     * @param deviceDataService 设备上报处理服务
     * @param objectMapper      JSON 序列化组件
     * @param signatureVerifier 设备签名校验组件
     */
    public MqttDeviceGateway(DeviceDataService deviceDataService, ObjectMapper objectMapper,
                             DeviceSignatureVerifier signatureVerifier) {
        this.deviceDataService = deviceDataService;
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
    }

    /**
     * 应用启动完成后异步建立连接，不阻塞主启动流程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        running = true;
        scheduler.execute(this::connect);
    }

    /**
     * 建立连接。首次失败按指数退避重试，不设总次数上限。
     */
    void connect() {
        if (!running) {
            return;
        }
        try {
            MqttClient newClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(false);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }
            newClient.setCallback(this);
            newClient.connect(options);
            client = newClient;
            reconnectAttempt = 0;
            log.info("MQTT 网关已连接 {}，clientId={}", brokerUrl, clientId);
        } catch (Exception e) {
            log.warn("MQTT 连接失败（第 {} 次）：{}", reconnectAttempt + 1, e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 连接完成回调：无论首次连接还是重连都重新订阅，避免持久会话未保留时丢订阅。
     *
     * @param reconnect true 表示本次是自动重连
     * @param serverURI Broker 地址
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        reconnectAttempt = 0;
        log.info("MQTT {} 成功：{}", reconnect ? "重连" : "连接", serverURI);
        subscribe();
    }

    /**
     * 订阅设备上报主题；订阅失败时触发重连。
     */
    private void subscribe() {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            return;
        }
        try {
            current.subscribe(topic, qos);
            log.info("MQTT 已订阅 {}（QoS {}）", topic, qos);
        } catch (Exception e) {
            log.warn("MQTT 订阅失败：{}", e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 连接断开回调：交由 Paho 自动重连，这里只记录日志。
     *
     * @param cause 断开原因，可能为空
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开，Paho 将自动重连：{}", cause == null ? "unknown" : cause.getMessage());
    }

    /**
     * 上行消息投递完成回调；当前只订阅设备上行主题，无需处理。
     *
     * @param token 投递令牌
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 仅上行订阅场景，无需处理投递回执
    }

    /**
     * 收到订阅主题消息时由 Paho 回调。
     *
     * @param messageTopic 实际投递的主题
     * @param message      消息内容
     */
    @Override
    public void messageArrived(String messageTopic, MqttMessage message) {
        onMessage(messageTopic, message);
    }

    /**
     * 处理一条 MQTT 消息：解析、签名校验、幂等去重后落库。
     *
     * @param messageTopic 实际投递的主题
     * @param message      消息内容
     */
    private void onMessage(String messageTopic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        try {
            handlePayload(payload);
            log.info("MQTT 收到设备上报 [{}]：{}", messageTopic, payload);
        } catch (DeviceAuthException e) {
            log.warn("MQTT 设备鉴权失败 [{}]：{}，payload={}", messageTopic, e.getMessage(), payload);
        } catch (Exception e) {
            log.warn("MQTT 消息处理失败 [{}]：{}", messageTopic, payload, e);
        }
    }

    /**
     * 解析并处理一条设备上报；鉴权开启时先校验签名。
     *
     * @param payload 设备上报 JSON 字符串
     * @throws DeviceAuthException 签名或凭证校验失败时抛出
     * @throws BusinessException   消息无法解析时抛出
     */
    void handlePayload(String payload) {
        DeviceGatewayReport report;
        try {
            report = objectMapper.readValue(payload, DeviceGatewayReport.class);
        } catch (Exception e) {
            throw new BusinessException("MQTT 消息解析失败：" + e.getMessage());
        }
        signatureVerifier.verifyReport(report);
        deviceDataService.handleGatewayReport(report, report.messageId(), "MQTT");
    }

    /**
     * 安排下一次重连，间隔按指数退避增长。
     */
    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        long delay = retryDelay(reconnectAttempt);
        reconnectAttempt++;
        scheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 计算指数退避间隔：1s、2s、4s……最长 60s。
     *
     * @param attempt 已失败次数，从 0 开始
     * @return 下一次重连前的等待毫秒数
     */
    static long retryDelay(int attempt) {
        int shift = Math.min(Math.max(attempt, 0), MAX_BACKOFF_SHIFT);
        return Math.min(INITIAL_RETRY_DELAY_MS << shift, MAX_RETRY_DELAY_MS);
    }

    /**
     * 发布一条测试上报（演示设备数据上行链路）。
     *
     * @param report 设备上报内容
     * @throws BusinessException 网关未连接或发布失败时抛出
     */
    public void publishTest(DeviceGatewayReport report) {
        MqttClient current = client;
        if (current == null || !current.isConnected()) {
            throw new BusinessException("MQTT 网关未连接");
        }
        try {
            String payload = objectMapper.writeValueAsString(report);
            String targetTopic = "factory/devices/" + report.deviceNo() + "/status";
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            current.publish(targetTopic, message);
        } catch (Exception e) {
            throw new BusinessException("MQTT 发布失败：" + e.getMessage());
        }
    }

    /**
     * 关闭网关：停止重连调度并断开连接。
     */
    @PreDestroy
    public void stop() {
        running = false;
        scheduler.shutdownNow();
        MqttClient current = client;
        if (current != null) {
            try {
                current.disconnect();
            } catch (Exception ignored) {
                // 关闭阶段忽略断开异常
            }
        }
    }
}
