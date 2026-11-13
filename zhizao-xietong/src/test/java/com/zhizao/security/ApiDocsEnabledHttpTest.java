package com.zhizao.security;

import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 显式开启接口文档时，Swagger 与 OpenAPI 路径进入公开白名单（仅用于内部或 dev 环境）。
 *
 * @author Caspian
 */
@WebMvcTest(controllers = ApiDocsEnabledHttpTest.Probe.class)
@ContextConfiguration(classes = ApiDocsEnabledHttpTest.EmptyConfig.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ApiDocsEnabledHttpTest.Probe.class})
@TestPropertySource(properties = "app.api-docs.enabled=true")
class ApiDocsEnabledHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    /** 占位端点：只用于观察过滤器链的判定结果。 */
    @RestController
    static class Probe {

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

    @Test
    void apiDocsAreReachableWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
