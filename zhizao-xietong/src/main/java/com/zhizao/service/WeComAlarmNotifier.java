package com.zhizao.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhizao.config.WeComNotifyProperties;
import com.zhizao.entity.AlarmRecord;
import com.zhizao.entity.AuditLog;
import com.zhizao.mapper.AuditLogMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信应用消息通知：唯一发送入口，默认关闭。
 *
 * <p>开关关闭或未配置 corpId/agentId/secret 时静默跳过，只记 debug；
 * 发送失败只记 WARN 并返回，不向调用方抛出，避免影响遥测入库与离线判定。</p>
 *
 * <p>外部调用超时由配置控制，推送在独立有界队列线程池异步执行并挂到事务提交之后，失败只补记审计，不占用上报线程。</p>
 *
 * @author Caspian
 */
@Component
public class WeComAlarmNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeComAlarmNotifier.class);
    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";
    /** 未返回 expires_in 时的兜底有效期，以及提前续期后至少保留的缓存时长（秒）。 */
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 7200L;
    private static final long MIN_TOKEN_TTL_SECONDS = 60L;
    /** access_token 提前续期秒数与获取失败后的退避秒数，避免外部异常时被持续重试。 */
    private static final long TOKEN_RENEW_SKEW_SECONDS = 300L;
    private static final long TOKEN_FAILURE_BACKOFF_SECONDS = 30L;
    /** access_token 失效/过期错误码，命中后只重新取一次 token。 */
    private static final Set<Integer> INVALID_TOKEN_CODES = Set.of(40014, 42001);
    private static final int MAX_ERROR_LENGTH = 255;

    private final RestClient restClient;
    private final AuditLogMapper auditLogMapper;
    private final WeComNotifyProperties properties;
    private final Executor executor;
    private final Object tokenLock = new Object();

    /** 缓存的 access_token；value 为 null 表示上次获取失败，retryAt 之前不再重试。 */
    private volatile CachedToken token;

    /** 固定外部调用超时，并创建单线程有界队列用于异步推送。 */
    public WeComAlarmNotifier(RestClient.Builder builder, AuditLogMapper auditLogMapper,
                              WeComNotifyProperties properties) {
        this(buildClient(builder, properties), auditLogMapper, properties,
                createExecutor(properties.getQueueCapacity()));
    }

    WeComAlarmNotifier(RestClient restClient, AuditLogMapper auditLogMapper,
                       WeComNotifyProperties properties, Executor executor) {
        this.restClient = restClient;
        this.auditLogMapper = auditLogMapper;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 投递一条报警通知；开关关闭、配置缺失或队列已满时静默跳过，任何情况都不抛出。
     *
     * <p>存在活动事务时在提交后投递，回滚则不发送。</p>
     */
    public void notify(AlarmRecord record) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.getCorpId()) || !StringUtils.hasText(properties.getAgentId())
                || !StringUtils.hasText(properties.getSecret())) {
            log.debug("企业微信报警通知未配置 corpId/agentId/secret，已跳过：deviceId={}", record.getDeviceId());
            return;
        }
        String content = content(record);
        Long deviceId = record.getDeviceId();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            submit(deviceId, content);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                submit(deviceId, content);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService pool) {
            pool.shutdownNow();
        }
    }

    private void submit(Long deviceId, String content) {
        try {
            executor.execute(() -> send(deviceId, content));
        } catch (RuntimeException e) {
            log.warn("企业微信报警通知队列已满或已关闭，丢弃本条：deviceId={}", deviceId);
        }
    }

    private void send(Long deviceId, String content) {
        try {
            String accessToken = accessToken();
            if (accessToken == null) {
                auditFailure(deviceId, "获取 access_token 失败");
                return;
            }
            SendResult result = sendText(accessToken, content);
            if (INVALID_TOKEN_CODES.contains(result.errcode())) {
                synchronized (tokenLock) {
                    token = null;
                }
                String refreshed = accessToken();
                if (refreshed == null) {
                    auditFailure(deviceId, "access_token 失效后重新获取失败");
                    return;
                }
                result = sendText(refreshed, content);
            }
            if (result.errcode() != 0) {
                log.warn("企业微信报警通知发送失败，已忽略：deviceId={}，errcode={}，errmsg={}",
                        deviceId, result.errcode(), result.errmsg());
                auditFailure(deviceId, "errcode=" + result.errcode() + ", errmsg=" + result.errmsg());
            }
        } catch (RuntimeException e) {
            log.warn("企业微信报警通知发送失败，已忽略：deviceId={}，原因={}", deviceId, e.getMessage());
            auditFailure(deviceId, e.getMessage());
        }
    }

    private SendResult sendText(String accessToken, String content) {
        Map<String, Object> body = Map.of(
                "touser", "@all",
                "msgtype", "text",
                "agentid", properties.getAgentId(),
                "text", Map.of("content", content));
        SendResult result = restClient.post()
                .uri(SEND_URL + "?access_token={token}", accessToken)
                .body(body)
                .retrieve().body(SendResult.class);
        return result == null ? new SendResult(-1, "empty response") : result;
    }

    /** 命中缓存直接复用并按 expires_in 提前续期；获取失败进入退避期，退避期内不再请求。 */
    private String accessToken() {
        synchronized (tokenLock) {
            long now = System.currentTimeMillis();
            if (token != null && token.usable(now)) {
                return token.value();
            }
            if (token != null && token.backingOff(now)) {
                return null;
            }
            return fetchToken();
        }
    }

    private String fetchToken() {
        try {
            TokenResponse response = restClient.get()
                    .uri(TOKEN_URL + "?corpid={corpId}&corpsecret={secret}",
                            properties.getCorpId(), properties.getSecret())
                    .retrieve().body(TokenResponse.class);
            if (response == null || response.errcode() != 0 || !StringUtils.hasText(response.accessToken())) {
                log.warn("企业微信 access_token 获取失败：{}", response == null ? "empty response"
                        : "errcode=" + response.errcode() + ", errmsg=" + response.errmsg());
                token = failedToken();
                return null;
            }
            long expiresIn = response.expiresIn() > 0 ? response.expiresIn() : DEFAULT_EXPIRES_IN_SECONDS;
            long ttlSeconds = Math.max(expiresIn - TOKEN_RENEW_SKEW_SECONDS, MIN_TOKEN_TTL_SECONDS);
            token = new CachedToken(response.accessToken(), System.currentTimeMillis() + ttlSeconds * 1000L, 0L);
            return response.accessToken();
        } catch (RuntimeException e) {
            token = failedToken();
            log.warn("企业微信 access_token 获取失败，{} 秒内不再重试：{}",
                    TOKEN_FAILURE_BACKOFF_SECONDS, e.getMessage());
            return null;
        }
    }

    private CachedToken failedToken() {
        return new CachedToken(null, 0L, System.currentTimeMillis() + TOKEN_FAILURE_BACKOFF_SECONDS * 1000L);
    }

    private void auditFailure(Long deviceId, String reason) {
        try {
            AuditLog entry = new AuditLog();
            entry.setUsername("system");
            entry.setMethod("WECOM");
            entry.setUri(SEND_URL);
            entry.setOperation("WeComAlarmNotifier.notify");
            entry.setParams("deviceId=" + deviceId);
            entry.setSuccess(false);
            entry.setErrorMessage(reason == null ? null
                    : reason.substring(0, Math.min(reason.length(), MAX_ERROR_LENGTH)));
            entry.setCreatedAt(LocalDateTime.now());
            auditLogMapper.insert(entry);
        } catch (RuntimeException e) {
            log.warn("企业微信通知失败审计写入失败，已忽略：{}", e.getMessage());
        }
    }

    private String content(AlarmRecord record) {
        return "设备报警：设备ID=" + record.getDeviceId()
                + "，类型=" + record.getRuleType()
                + "，指标=" + (StringUtils.hasText(record.getMetric()) ? record.getMetric() : "-")
                + "，触发值=" + (record.getTriggerValue() == null ? "-" : record.getTriggerValue())
                + "，阈值=" + record.getThresholdSnapshot();
    }

    private static RestClient buildClient(RestClient.Builder builder, WeComNotifyProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(properties.getConnectTimeoutMs(), 1)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(properties.getReadTimeoutMs(), 1)));
        return builder.requestFactory(factory).build();
    }

    private static Executor createExecutor(int queueCapacity) {
        return new ThreadPoolExecutor(1, 1, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(queueCapacity, 1)), runnable -> {
                    Thread thread = new Thread(runnable, "wecom-notify");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private record CachedToken(String value, long expiresAt, long retryAt) {
        boolean usable(long now) {
            return value != null && now < expiresAt;
        }
        boolean backingOff(long now) {
            return value == null && now < retryAt;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") long expiresIn,
                                 @JsonProperty("errcode") int errcode,
                                 @JsonProperty("errmsg") String errmsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendResult(@JsonProperty("errcode") int errcode, @JsonProperty("errmsg") String errmsg) {
    }
}
