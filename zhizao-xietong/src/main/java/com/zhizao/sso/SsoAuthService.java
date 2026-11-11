package com.zhizao.sso;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.config.SsoProperties;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.SsoStatus;
import com.zhizao.entity.SysUser;
import com.zhizao.security.JwtUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 企业微信登录编排：灰度开关 → 限流 → 校验一次性 state → 换取外部身份 → JIT 复用/建档 → 签发本地 JWT。
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
    private final SsoStateStore stateStore;

    public SsoAuthService(SsoProperties properties, EnterpriseIdentityProvider provider,
                          LocalIdentityResolver identityResolver, JwtUtil jwtUtil,
                          LoginRateLimiter loginRateLimiter, SsoStateStore stateStore) {
        this.properties = properties;
        this.provider = provider;
        this.identityResolver = identityResolver;
        this.jwtUtil = jwtUtil;
        this.loginRateLimiter = loginRateLimiter;
        this.stateStore = stateStore;
    }

    /**
     * 企微登录：灰度开关 → 限流 → 消费 state → 换 userid → JIT → 本地 JWT。
     *
     * <p>state 校验失败直接拒绝，不降级为跳过校验继续登录。</p>
     *
     * @param code         企微回调携带的授权码
     * @param state        本服务签发且未被消费的 state
     * @param browserToken 当前请求 Cookie 中的浏览器标识
     * @param clientIp     客户端 IP，用于按 IP 限流
     * @return 与本地登录同结构的登录响应
     */
    public LoginResponse login(String code, String state, String browserToken, String clientIp) {
        requireEnabled();
        loginRateLimiter.check("sso:" + (clientIp == null ? "unknown" : clientIp));
        requireFreshState(state, browserToken);
        IdentityUser identity = provider.exchange(code, properties.getWecom().getRedirectUri());
        SysUser user = identityResolver.resolve(identity);
        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), tokenVersion);
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole(),
                Integer.valueOf(1).equals(user.getMustChangePassword()));
    }

    /**
     * 生成扫码授权地址，并把新 state 绑定到浏览器标识。
     *
     * @param existingBrowserToken Cookie 中已有的浏览器标识，为空时新建
     * @return 扫码地址与应写回 Cookie 的浏览器标识
     */
    public AuthorizeRedirect authorize(String existingBrowserToken) {
        requireEnabled();
        String browserToken = StringUtils.hasText(existingBrowserToken)
                ? existingBrowserToken : stateStore.newBrowserToken();
        String state = stateStore.issue(browserToken);
        return new AuthorizeRedirect(provider.authorizeUrl(state, properties.getWecom().getRedirectUri()),
                browserToken);
    }

    /**
     * 查询灰度开关状态，供登录页决定是否展示扫码入口；关闭时返回 false 而不是拒绝。
     *
     * @return SSO 可用状态
     */
    public SsoStatus status() {
        return new SsoStatus(properties.isEnabled());
    }

    private void requireFreshState(String state, String browserToken) {
        if (!StringUtils.hasText(state) || !StringUtils.hasText(browserToken)) {
            throw new BadRequestException("缺少登录会话信息，请重新扫码登录");
        }
        if (!stateStore.consume(state, browserToken)) {
            throw new BadRequestException("登录会话已失效，请重新扫码登录");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new BusinessException(503, "企业微信登录未启用");
        }
    }

    /**
     * 授权跳转结果。
     *
     * @param url          企微扫码页地址
     * @param browserToken 需要写入 Cookie 的浏览器标识
     */
    public record AuthorizeRedirect(String url, String browserToken) {
    }
}
