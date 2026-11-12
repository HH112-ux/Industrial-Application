package com.zhizao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信报警通知配置：默认关闭，连接/读取超时与队列容量可调；不实现 {@code toString()} 以免 secret 进日志。
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
}
