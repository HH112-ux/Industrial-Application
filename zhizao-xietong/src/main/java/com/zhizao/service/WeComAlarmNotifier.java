package com.zhizao.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhizao.entity.AlarmRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业微信应用消息通知：唯一发送入口，默认关闭。
 *
 * <p>开关关闭或未配置 corpId/agentId/secret 时静默跳过，只记 debug；
 * 发送失败只记 WARN 并返回，不向调用方抛出，避免影响遥测入库与离线判定。</p>
 *
 * @author Caspian
 */
@Component
public class WeComAlarmNotifier {

    private static final Logger log = LoggerFactory.getLogger(WeComAlarmNotifier.class);
    private static final String TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken";
    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/message/send";

    private final RestClient restClient;

    @Value("${device.alarm.notify.wecom.enabled:false}")
    private boolean enabled;
    @Value("${device.alarm.notify.wecom.corp-id:}")
    private String corpId = "";
    @Value("${device.alarm.notify.wecom.agent-id:}")
    private String agentId = "";
    @Value("${device.alarm.notify.wecom.secret:}")
    private String secret = "";

    /**
     * 构造通知器。
     *
     * @param builder Spring 提供的 RestClient 构建器
     */
    public WeComAlarmNotifier(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /**
     * 发送一条报警文本；开关关闭、配置缺失或发送失败时都不抛出。
     *
     * @param record 新开的报警记录
     */
    public void notify(AlarmRecord record) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(corpId) || !StringUtils.hasText(agentId) || !StringUtils.hasText(secret)) {
            log.debug("企业微信报警通知未配置 corpId/agentId/secret，已跳过：deviceId={}", record.getDeviceId());
            return;
        }
        try {
            Token token = restClient.get()
                    .uri(TOKEN_URL + "?corpid={corpId}&corpsecret={secret}", corpId, secret)
                    .retrieve().body(Token.class);
            if (token == null || !StringUtils.hasText(token.accessToken())) {
                log.warn("企业微信报警通知获取 access_token 失败，已跳过");
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("touser", "@all");
            body.put("msgtype", "text");
            body.put("agentid", agentId);
            body.put("text", Map.of("content", content(record)));
            restClient.post()
                    .uri(SEND_URL + "?access_token={token}", token.accessToken())
                    .body(body)
                    .retrieve().body(String.class);
        } catch (RuntimeException e) {
            log.warn("企业微信报警通知发送失败，已忽略：deviceId={}，原因={}",
                    record.getDeviceId(), e.getMessage());
        }
    }

    private String content(AlarmRecord record) {
        return "设备报警：设备ID=" + record.getDeviceId()
                + "，类型=" + record.getRuleType()
                + "，指标=" + (StringUtils.hasText(record.getMetric()) ? record.getMetric() : "-")
                + "，触发值=" + (record.getTriggerValue() == null ? "-" : record.getTriggerValue())
                + "，阈值=" + record.getThresholdSnapshot();
    }

    /** 企业微信 access_token 响应。 */
    private record Token(@JsonProperty("access_token") String accessToken) {
    }
}
