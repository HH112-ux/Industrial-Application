package com.zhizao.controller;

import com.zhizao.common.ForbiddenException;
import com.zhizao.common.GlobalExceptionHandler;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtAuthenticationFilter;
import com.zhizao.security.JwtUtil;
import com.zhizao.security.SecurityConfig;
import com.zhizao.service.DeviceDataService;
import com.zhizao.security.DeviceSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 遥测接口的越权测试：访问其他车间设备时 HTTP 层必须返回 403。
 *
 * @author Caspian
 */
@WebMvcTest(controllers = DeviceDataController.class)
@ContextConfiguration(classes = DeviceTelemetryScopeHttpTest.EmptyConfig.class)
@Import({DeviceDataController.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class})
class DeviceTelemetryScopeHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceDataService deviceDataService;

    @MockBean
    private DeviceSignatureVerifier signatureVerifier;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SysUserMapper userMapper;

    /**
     * 构造一个已启用、令牌版本匹配的非管理员用户。
     *
     * @return 用户实体
     */
    private SysUser operator() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("lisi");
        user.setRole("USER");
        user.setEnabled(1);
        user.setTokenVersion(0);
        user.setWorkshopScope("一车间");
        return user;
    }

    /**
     * 准备通过认证的请求上下文。
     */
    private void authenticate() {
        when(jwtUtil.validate("token")).thenReturn(true);
        when(jwtUtil.getUsername("token")).thenReturn("lisi");
        when(jwtUtil.getTokenVersion("token")).thenReturn(0);
        when(userMapper.selectOne(any())).thenReturn(operator());
    }

    @Test
    void telemetryOfDeviceInOtherWorkshopReturns403() throws Exception {
        authenticate();
        when(deviceDataService.getTelemetry(9L)).thenThrow(new ForbiddenException("无权访问其他车间的数据"));

        mockMvc.perform(get("/api/devices/9/telemetry").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void telemetryHistoryOfDeviceInOtherWorkshopReturns403() throws Exception {
        authenticate();
        when(deviceDataService.historyResult(9L, null, null, null))
                .thenThrow(new ForbiddenException("无权访问其他车间的数据"));

        mockMvc.perform(get("/api/devices/9/telemetry/history").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
    }
}
