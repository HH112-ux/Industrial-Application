package com.zhizao.controller;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ClientIpResolver;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.common.Result;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.SsoLoginRequest;
import com.zhizao.dto.SsoStatus;
import com.zhizao.service.AuthAuditService;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import com.zhizao.sso.SsoStateStore;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SsoAuthService ssoAuthService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuthAuditService authAuditService;
    private final ClientIpResolver clientIpResolver;
    private final boolean cookieSecure;

    public AuthController(AuthService authService, SsoAuthService ssoAuthService,
                          LoginRateLimiter loginRateLimiter, AuthAuditService authAuditService,
                          ClientIpResolver clientIpResolver,
                          @Value("${app.sso.cookie-secure:true}") boolean cookieSecure) {
        this.authService = authService;
        this.ssoAuthService = ssoAuthService;
        this.loginRateLimiter = loginRateLimiter;
        this.authAuditService = authAuditService;
        this.clientIpResolver = clientIpResolver;
        this.cookieSecure = cookieSecure;
    }

    /** 本地登录：限流、口令错误、账号禁用与需扫码均落认证审计；审计不接收请求体。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        try {
            loginRateLimiter.check(request.username());
        } catch (BusinessException e) {
            authAuditService.record(AuthAuditService.EVENT_LOGIN, request.username(), false,
                    AuthAuditService.RATE_LIMITED, e.getMessage(), ip);
            throw e;
        }
        try {
            LoginResponse response = authService.login(request);
            authAuditService.record(AuthAuditService.EVENT_LOGIN, request.username(), true,
                    AuthAuditService.SUCCESS, null, ip);
            return Result.ok(response);
        } catch (ForbiddenException e) {
            authAuditService.record(AuthAuditService.EVENT_LOGIN, request.username(), false,
                    AuthAuditService.ACCOUNT_DISABLED, e.getMessage(), ip);
            throw e;
        } catch (BusinessException e) {
            String result = AuthService.SSO_LOGIN_HINT.equals(e.getMessage())
                    ? AuthAuditService.SSO_REQUIRED : AuthAuditService.BAD_CREDENTIALS;
            authAuditService.record(AuthAuditService.EVENT_LOGIN, request.username(), false,
                    result, e.getMessage(), ip);
            throw e;
        }
    }

    /** 刷新令牌：用当前有效令牌换取新令牌（滑动续期）。 */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        return Result.ok(authService.refresh(token));
    }

    /** 登出：无状态 JWT 由客户端丢弃令牌，服务端仅提供统一入口并留痕。 */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest servletRequest) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        authAuditService.record(AuthAuditService.EVENT_LOGOUT,
                authentication != null ? authentication.getName() : null,
                true, AuthAuditService.SUCCESS, null, clientIp(servletRequest));
        return Result.ok();
    }

    /** 企业微信扫码登录：先校验一次性 state，再用回调 code 换取本地 JWT；开关关闭时明确拒绝。 */
    @PostMapping("/sso/wecom/login")
    public Result<LoginResponse> wecomLogin(@RequestBody SsoLoginRequest request,
                                            @CookieValue(name = SsoStateStore.BROWSER_COOKIE, required = false)
                                            String browserToken,
                                            HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        try {
            LoginResponse response = ssoAuthService.login(request.code(), request.state(), browserToken, ip);
            authAuditService.record(AuthAuditService.EVENT_SSO_LOGIN, response.username(), true,
                    AuthAuditService.SUCCESS, null, ip);
            return Result.ok(response);
        } catch (ForbiddenException e) {
            authAuditService.record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                    ssoRejectedCode(e), e.getMessage(), ip);
            throw e;
        } catch (BadRequestException e) {
            authAuditService.record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                    ssoBadRequestCode(e), e.getMessage(), ip);
            throw e;
        } catch (BusinessException e) {
            authAuditService.record(AuthAuditService.EVENT_SSO_LOGIN, null, false,
                    e.getCode() == 503 ? AuthAuditService.SSO_DISABLED : AuthAuditService.SSO_FAILED,
                    e.getMessage(), ip);
            throw e;
        }
    }

    /** SSO 灰度开关状态：登录页据此显隐企业微信入口，关闭时仍返回 200。 */
    @GetMapping("/sso/config")
    public Result<SsoStatus> ssoConfig() {
        return Result.ok(ssoAuthService.status());
    }

    /**
     * 企微扫码授权跳转：签发一次性 state 并写入浏览器标识 Cookie。
     *
     * <p>已存在 Cookie 时复用同一标识，因此同时打开多个登录页都能各自完成扫码。</p>
     */
    @GetMapping("/sso/wecom/authorize")
    public ResponseEntity<Void> wecomAuthorize(
            @CookieValue(name = SsoStateStore.BROWSER_COOKIE, required = false) String browserToken,
            HttpServletResponse response) {
        SsoAuthService.AuthorizeRedirect redirect = ssoAuthService.authorize(browserToken);
        ResponseCookie cookie = ResponseCookie.from(SsoStateStore.BROWSER_COOKIE, redirect.browserToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirect.url()))
                .build();
    }

    /** SSO 拒绝分类：提示含禁用/停用归为 ACCOUNT_DISABLED，其余归为 SSO_REJECTED。 */
    private String ssoRejectedCode(ForbiddenException e) {
        String message = e.getMessage();
        return message != null && (message.contains("禁用") || message.contains("停用"))
                ? AuthAuditService.ACCOUNT_DISABLED : AuthAuditService.SSO_REJECTED;
    }

    /** SSO 参数错误分类：授权码问题归为 INVALID_TICKET，state/会话问题归为 INVALID_STATE。 */
    private String ssoBadRequestCode(BadRequestException e) {
        String message = e.getMessage();
        return message != null && message.contains("授权码")
                ? AuthAuditService.INVALID_TICKET : AuthAuditService.INVALID_STATE;
    }

    /** 取客户端 IP：仅在请求来自可信代理时才采信 X-Forwarded-For，其余直接用 TCP 对端地址。 */
    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
