package com.zhizao.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.config.SsoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业微信网页授权实现：gettoken → auth/getuserinfo → user/get。
 *
 * <p>外部主键只认 {@code userid}；{@code openid} 属于外部联系人，一律拒绝。成员详情读取失败
 * 只降级为仅有 userid 的身份，不阻断登录；成员状态为未激活/已禁用/已离职时标记为不可用。</p>
 *
 * @author Caspian
 */
@Component
public class WeComIdentityProvider implements EnterpriseIdentityProvider {

    public static final String PROVIDER = "WEIXIN_WORK";
    private static final Logger LOGGER = LoggerFactory.getLogger(WeComIdentityProvider.class);
    private static final String QR_CONNECT = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect";
    /** access_token 提前 5 分钟过期，避免临界时刻用到失效令牌。 */
    private static final long TOKEN_TTL_MARGIN_SECONDS = 300;

    private final SsoProperties properties;
    private final RestClient restClient;
    private String accessToken;
    private long accessTokenExpiresAt;

    public WeComIdentityProvider(SsoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
        if (properties.isEnabled()) {
            require(properties.getWecom().getCorpId(), "SSO_WECOM_CORP_ID");
            require(properties.getWecom().getAgentId(), "SSO_WECOM_AGENT_ID");
            require(properties.getWecom().getSecret(), "SSO_WECOM_SECRET");
            require(properties.getWecom().getRedirectUri(), "SSO_WECOM_REDIRECT_URI");
        }
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        SsoProperties.Wecom wecom = properties.getWecom();
        return QR_CONNECT + "?appid=" + wecom.getCorpId() + "&agentid=" + wecom.getAgentId()
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    @Override
    public IdentityUser exchange(String code, String redirectUri) {
        if (!StringUtils.hasText(code)) {
            throw new BadRequestException("缺少企业微信授权码");
        }
        String token = accessToken();
        UserInfo userInfo = restClient.get()
                .uri(properties.getWecom().getBaseUrl() + "/cgi-bin/auth/getuserinfo?access_token={token}&code={code}",
                        token, code)
                .retrieve()
                .body(UserInfo.class);
        if (userInfo == null || userInfo.errcode() != 0) {
            throw new BadRequestException("企业微信授权码无效或已过期");
        }
        if (!StringUtils.hasText(userInfo.userId())) {
            throw new ForbiddenException("仅限本企业成员登录");
        }
        Member member = member(token, userInfo.userId());
        return new IdentityUser(PROVIDER, userInfo.userId(), userInfo.userId(),
                member == null ? userInfo.userId() : member.name(),
                member == null ? null : member.mobile(),
                member == null ? null : member.email(),
                member == null || member.department() == null ? List.of() : departments(member.department()),
                member == null || member.status() == null || member.status() == 1);
    }

    /** 成员详情拉取失败只降级，不影响 userid 已确认的登录。 */
    private Member member(String token, String userId) {
        Member member = restClient.get()
                .uri(properties.getWecom().getBaseUrl() + "/cgi-bin/user/get?access_token={token}&userid={userId}",
                        token, userId)
                .retrieve()
                .body(Member.class);
        if (member == null || member.errcode() != 0) {
            LOGGER.warn("企业微信成员详情读取失败，按最小身份继续登录 userId={}", userId);
            return null;
        }
        return member;
    }

    private synchronized String accessToken() {
        long now = System.currentTimeMillis();
        if (accessToken != null && now < accessTokenExpiresAt) {
            return accessToken;
        }
        Token token = restClient.get()
                .uri(properties.getWecom().getBaseUrl() + "/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}",
                        properties.getWecom().getCorpId(), properties.getWecom().getSecret())
                .retrieve()
                .body(Token.class);
        if (token == null || token.errcode() != 0 || !StringUtils.hasText(token.accessToken())) {
            throw new BusinessException(502, "企业微信鉴权失败，请稍后重试");
        }
        accessToken = token.accessToken();
        accessTokenExpiresAt = now + Math.max(60, token.expiresIn() - TOKEN_TTL_MARGIN_SECONDS) * 1000;
        return accessToken;
    }

    private List<String> departments(List<Long> departmentIds) {
        return departmentIds.stream().map(String::valueOf).collect(Collectors.toList());
    }

    private void require(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("app.sso.enabled=true 时必须配置 " + envName);
        }
    }

    private record Token(int errcode, String errmsg,
                         @JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }

    private record UserInfo(int errcode, String errmsg,
                            @JsonProperty("userid") String userId,
                            @JsonProperty("openid") String openId) {
    }

    private record Member(int errcode, String errmsg, String name, String mobile, String email,
                          List<Long> department, Integer status) {
    }
}
