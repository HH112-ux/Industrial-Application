package com.zhizao.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 暴露面收敛：接口文档默认不对外开放，Actuator 指标只允许 ADMIN 读取。
 *
 * <p>用探针 Controller 占位 Swagger 与 metrics 路径，验证的是安全过滤链的放行/拦截规则。</p>
 *
 * @author Caspian
 */
@WebMvcTest(controllers = SecurityExposureHttpTest.Probe.class)
@ContextConfiguration(classes = SecurityExposureHttpTest.EmptyConfig.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, SecurityExposureHttpTest.Probe.class})
class SecurityExposureHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    /** 占位端点：只用于观察过滤器链的判定结果。 */
    @RestController
    static class Probe {

        @GetMapping("/actuator/metrics")
        String metrics() {
            return "metrics";
        }

        @GetMapping("/v3/api-docs")
        String apiDocs() {
            return "{}";
        }

        @GetMapping("/swagger-ui/index.html")
        String swaggerUi() {
            return "ui";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SysUserMapper userMapper;

    private void authenticateAs(String role) {
        when(jwtUtil.validate("token")).thenReturn(true);
        when(jwtUtil.getUsername("token")).thenReturn("operator");
        when(jwtUtil.getTokenVersion("token")).thenReturn(0);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user(role));
    }

    private SysUser user(String role) {
        SysUser user = new SysUser();
        user.setUsername("operator");
        user.setRole(role);
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setMustChangePassword(0);
        return user;
    }

    @Test
    void metricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void metricsRejectNonAdminUsers() throws Exception {
        authenticateAs("WORKER");

        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void metricsAllowAdminUsers() throws Exception {
        authenticateAs("ADMIN");

        mockMvc.perform(get("/actuator/metrics").header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocsAreNotPublicByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
}
