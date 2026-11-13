package com.zhizao.security;

import com.zhizao.common.ClientIpResolver;
import com.zhizao.common.LoginRateLimiter;
import com.zhizao.controller.AuthController;
import com.zhizao.dto.LoginResponse;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.service.AuthAuditService;
import com.zhizao.service.AuthService;
import com.zhizao.sso.SsoAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 CORS 白名单配置生效：白名单内的前端源可正常登录，白名单外的源被 403 拒绝。
 *
 * <p>对应部署演练的 CORS 阻断：容器未注入 {@code CORS_ALLOWED_ORIGINS} 时后端回落
 * localhost，浏览器登录被 {@code 403 Invalid CORS request} 拦截。</p>
 *
 * @author Caspian
 */
@WebMvcTest(controllers = AuthController.class)
@ContextConfiguration(classes = CorsHttpTest.EmptyConfig.class)
@Import({AuthController.class, SecurityConfig.class, JwtAuthenticationFilter.class, ClientIpResolver.class})
@TestPropertySource(properties = "app.cors.allowed-origins=https://mes.example.com")
class CorsHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    private static final String LOGIN_BODY = "{\"username\":\"admin\",\"password\":\"secret\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private SsoAuthService ssoAuthService;

    @MockBean
    private LoginRateLimiter loginRateLimiter;

    @MockBean
    private AuthAuditService authAuditService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SysUserMapper userMapper;

    @Test
    void allowsLoginFromConfiguredOrigin() throws Exception {
        when(authService.login(any()))
                .thenReturn(new LoginResponse("token", "admin", "管理员", "ADMIN", false));

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://mes.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://mes.example.com"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rejectsLoginFromOriginOutsideAllowList() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "http://localhost:8081")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isForbidden());
    }
}
