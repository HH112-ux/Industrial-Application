package com.zhizao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信报警通知配置：默认关闭，外部调用超时、异步队列与 token 续期策略均可调。
 *
 * <p>刻意不实现 {@code toString()}，避免 secret 因对象打印进入日志。</p>
 *
 * @author Caspian
 */
@Component
@ConfigurationProperties(prefix = "device.alarm.notify.wecom")
public class WeComNotifyProperties {

    private boolean enabled = false;
    private String corpId = "";
    private String agentId = "";
    private String secret = "";
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 3000;
    private int queueCapacity = 200;
    private int tokenRenewSkewSeconds = 300;
    private int tokenFailureBackoffSeconds = 30;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCorpId() { return corpId; }
    public void setCorpId(String corpId) { this.corpId = corpId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getTokenRenewSkewSeconds() { return tokenRenewSkewSeconds; }
    public void setTokenRenewSkewSeconds(int seconds) { this.tokenRenewSkewSeconds = seconds; }
    public int getTokenFailureBackoffSeconds() { return tokenFailureBackoffSeconds; }
    public void setTokenFailureBackoffSeconds(int seconds) { this.tokenFailureBackoffSeconds = seconds; }
}
