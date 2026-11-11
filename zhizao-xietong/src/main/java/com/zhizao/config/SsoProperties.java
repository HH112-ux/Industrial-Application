package com.zhizao.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业统一身份（SSO）配置。密钥只从环境变量注入，本类刻意不实现 {@code toString()}。
 *
 * <p>{@code enabled=true} 且缺少 corpId/agentId/secret/redirectUri 时，企微适配器构造失败，
 * 应用启动直接失败，不做静默降级。</p>
 *
 * @author Caspian
 */
@Component
@ConfigurationProperties(prefix = "app.sso")
public class SsoProperties {

    private boolean enabled = false;
    /** 是否允许首次扫码自动建号；本地为主后默认关闭，未知企微用户需管理员先开通。 */
    private boolean jitEnabled = false;
    private String provider = "WEIXIN_WORK";
    private Wecom wecom = new Wecom();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isJitEnabled() {
        return jitEnabled;
    }

    public void setJitEnabled(boolean jitEnabled) {
        this.jitEnabled = jitEnabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Wecom getWecom() {
        return wecom;
    }

    public void setWecom(Wecom wecom) {
        this.wecom = wecom;
    }

    /**
     * 企业微信应用凭据。secret 与会话密钥不同，轮换周期以企微后台为准。
     */
    public static class Wecom {

        private String corpId;
        private String agentId;
        private String secret;
        private String redirectUri;
        private String baseUrl = "https://qyapi.weixin.qq.com";

        public String getCorpId() {
            return corpId;
        }

        public void setCorpId(String corpId) {
            this.corpId = corpId;
        }

        public String getAgentId() {
            return agentId;
        }

        public void setAgentId(String agentId) {
            this.agentId = agentId;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
