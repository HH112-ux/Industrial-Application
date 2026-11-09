package com.zhizao.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhizao.common.BusinessException;
import com.zhizao.common.DeviceAuthException;
import com.zhizao.dto.DeviceGatewayReport;
import com.zhizao.security.DeviceSignatureVerifier;
import com.zhizao.service.DeviceDataService;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
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
 * <p>重连策略：只保留手动指数退避重连（Paho 自动重连已关闭），避免两条重连路径
 * 各建一个同 clientId 的客户端互相踢下线。持久会话（cleanSession=false）下若
 * CONNACK 返回 sessionPresent=false，连接后显式重新订阅；替换客户端前先关闭旧实例。</p>
 * <p>多实例部署时 `mqtt.client-id` 必须每实例唯一，否则会互相顶掉会话。</p>
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
     * 建立连接。失败按指数退避重试，不设总次数上限。
     *
     * <p>先把新客户端赋值给 {@link #client} 再发起连接：Paho 会在 connect 调用内部
     * 回调 {@link #connectComplete}，若此时字段仍为 null，订阅会被静默丢弃。</p>
     */
    void connect() {
        if (!running) {
            return;
        }
        try {
            closeQuietly(client);
            client = null;
            MqttClient newClient = createClient();
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(false);
            options.setAutomaticReconnect(false);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            if (StringUtils.hasText(username)) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }
            newClient.setCallback(this);
            client = newClient;
            IMqttToken token = newClient.connectWithResult(options);
            reconnectAttempt = 0;
            boolean sessionPresent = token.getSessionPresent();
            log.info("MQTT 网关已连接 {}，clientId={}，sessionPresent={}", brokerUrl, clientId, sessionPresent);
            if (sessionPresent) {
                log.info("MQTT 复用 Broker 保留的会话，订阅继续有效");
            } else {
                subscribe(newClient);
            }
        } catch (Exception e) {
            log.warn("MQTT 连接失败（第 {} 次）：{}", reconnectAttempt + 1, e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * 创建 MQTT 客户端，供测试替换。
     *
     * @return 新的 MQTT 客户端
     * @throws org.eclipse.paho.client.mqttv3.MqttException 客户端初始化失败时抛出
     */
    MqttClient createClient() throws org.eclipse.paho.client.mqttv3.MqttException {
        return new MqttClient(brokerUrl, clientId, new MemoryPersistence());
    }

    /**
     * 关闭客户端并忽略异常，用于替换客户端与停机场景。
     *
     * @param current 待关闭的客户端，可为空
     */
    private void closeQuietly(MqttClient current) {
        if (current == null) {
            return;
        }
        try {
            current.disconnectForcibly(0, 0);
        } catch (Exception ignored) {
            // 连接已断开时忽略
        }
        try {
            current.close(true);
        } catch (Exception ignored) {
            // 客户端已关闭时忽略
        }
    }

    /**
     * 连接完成回调：由 Paho 在 connect 内部触发，此时 {@link #client} 已赋值。
     *
     * <p>订阅统一在 {@link #connect()} 拿到 CONNACK 的 sessionPresent 后执行，
     * 这里只记录日志，避免首次连接时订阅两次。</p>
     *
     * @param reconnect true 表示本次是重连
     * @param serverURI Broker 地址
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        reconnectAttempt = 0;
        log.info("MQTT {} 成功：{}", reconnect ? "重连" : "连接", serverURI);
    }

    /**
     * 订阅设备上报主题。
     *
     * @param current 目标客户端
     * @return 订阅成功返回 true；客户端未就绪时返回 false 并安排重连
     */
    boolean subscribe(MqttClient current) {
        if (current == null || !current.isConnected()) {
            log.warn("MQTT 客户端未就绪，订阅未执行，将安排重连");
            scheduleReconnect();
            return false;
        }
        try {
            current.subscribe(topic, qos);
            log.info("MQTT 已订阅 {}（QoS {}）", topic, qos);
            return true;
        } catch (Exception e) {
            log.warn("MQTT 订阅失败：{}", e.getMessage());
            scheduleReconnect();
            return false;
        }
    }

    /**
     * 连接断开回调：关闭 Paho 自动重连后由本类统一安排指数退避重连。
     *
     * @param cause 断开原因，可能为空
     */
    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT 连接断开，将按退避策略重连：{}", cause == null ? "unknown" : cause.getMessage());
        scheduleReconnect();
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
    void scheduleReconnect() {
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
        closeQuietly(client);
        client = null;
    }
}
