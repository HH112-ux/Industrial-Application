package com.zhizao.controller;

import com.zhizao.common.LoginRateLimiter;
import com.zhizao.common.Result;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.dto.SsoLoginRequest;
import com.zhizao.dto.SsoStatus;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SsoAuthService ssoAuthService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, SsoAuthService ssoAuthService,
                          LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.ssoAuthService = ssoAuthService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        loginRateLimiter.check(request.username());
        return Result.ok(authService.login(request));
    }

    /** 刷新令牌：用当前有效令牌换取新令牌（滑动续期）。 */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        return Result.ok(authService.refresh(token));
    }

    /** 登出：无状态 JWT 由客户端丢弃令牌，服务端仅提供统一入口。 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.ok();
    }

    /** 企业微信扫码登录：用回调 code 换取本地 JWT；开关关闭时明确拒绝。 */
    @PostMapping("/sso/wecom/login")
    public Result<LoginResponse> wecomLogin(@RequestBody SsoLoginRequest request,
                                            HttpServletRequest servletRequest) {
        return Result.ok(ssoAuthService.login(request.code(), clientIp(servletRequest)));
    }

    /** SSO 灰度开关状态：登录页据此显隐企业微信入口，关闭时仍返回 200。 */
    @GetMapping("/sso/config")
    public Result<SsoStatus> ssoConfig() {
        return Result.ok(ssoAuthService.status());
    }

    /** 企微扫码授权跳转：登录页在开关开启时跳转到该地址。 */
    @GetMapping("/sso/wecom/authorize")
    public ResponseEntity<Void> wecomAuthorize() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(ssoAuthService.authorizeUrl()))
                .build();
    }

    /** 取客户端 IP：优先 X-Forwarded-For 首个地址，与登录审计口径一致。 */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
