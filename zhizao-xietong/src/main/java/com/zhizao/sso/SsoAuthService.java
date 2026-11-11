package com.zhizao.sso;

import com.zhizao.common.BusinessException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginResponse;
import com.zhizao.entity.SysUser;
import com.zhizao.security.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 企业微信登录编排：灰度开关 → 限流 → 换取外部身份 → JIT 复用/建档 → 签发本地 JWT。
 *
 * <p>只复用既有认证链路：签发的令牌结构与本地登录一致（sub/role/tv），
 * {@code JwtAuthenticationFilter} 的 enabled + tokenVersion 即时失效语义不变。</p>
 *
 * @author Caspian
 */
@Service
public class SsoAuthService {

    private final SsoProperties properties;
    private final EnterpriseIdentityProvider provider;
    private final LocalIdentityResolver identityResolver;
    private final JwtUtil jwtUtil;
    private final LoginRateLimiter loginRateLimiter;

    public SsoAuthService(SsoProperties properties, EnterpriseIdentityProvider provider,
                          LocalIdentityResolver identityResolver, JwtUtil jwtUtil,
                          LoginRateLimiter loginRateLimiter) {
        this.properties = properties;
        this.provider = provider;
        this.identityResolver = identityResolver;
        this.jwtUtil = jwtUtil;
        this.loginRateLimiter = loginRateLimiter;
    }

    /**
     * 企微登录：授权码一次性，换取 userid 后复用或建档，再签发本地 JWT。
     *
     * @param code     企微回调携带的授权码
     * @param clientIp 客户端 IP，用于按 IP 限流
     * @return 与本地登录同结构的登录响应
     */
    public LoginResponse login(String code, String clientIp) {
        requireEnabled();
        loginRateLimiter.check("sso:" + (clientIp == null ? "unknown" : clientIp));
        IdentityUser identity = provider.exchange(code, properties.getWecom().getRedirectUri());
        SysUser user = identityResolver.resolve(identity);
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), tokenVersion);
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole(),
                Integer.valueOf(1).equals(user.getMustChangePassword()));
    }

    /**
     * 生成企微扫码授权地址；redirectUri 取配置中的前端回调地址。
     *
     * @return 企微扫码页地址
     */
    public String authorizeUrl() {
        requireEnabled();
        return provider.authorizeUrl(UUID.randomUUID().toString(), properties.getWecom().getRedirectUri());
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "企业微信登录未启用");
        }
    }
}
