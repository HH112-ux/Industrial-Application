package com.zhizao.controller;

import com.zhizao.common.LoginRateLimiter;
import com.zhizao.common.Result;
import com.zhizao.dto.LoginRequest;
import com.zhizao.dto.LoginResponse;
import com.zhizao.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
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
}
