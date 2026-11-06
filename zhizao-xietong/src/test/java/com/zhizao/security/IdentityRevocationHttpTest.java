package com.zhizao.security;

import com.zhizao.common.PageResult;
import com.zhizao.controller.DeviceController;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证禁用与令牌版本在 HTTP 层的即时生效：被禁用或版本落后的令牌，下一次请求直接 401。
 *
 * @author Caspian
 */
@WebMvcTest(controllers = DeviceController.class)
@ContextConfiguration(classes = IdentityRevocationHttpTest.EmptyConfig.class)
@Import({DeviceController.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class IdentityRevocationHttpTest {

    /**
     * 隔离主应用的 @MapperScan，避免 MVC 切片测试拉起数据库映射。
     */
    @SpringBootConfiguration
    static class EmptyConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private SysUserMapper userMapper;

    private SysUser user(int enabled, int tokenVersion) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setRole("USER");
        user.setEnabled(enabled);
        user.setTokenVersion(tokenVersion);
        user.setWorkshopScope("二车间");
        return user;
    }

    @Test
    void disabledTokenGets401OnTheNextRequest() throws Exception {
        when(jwtUtil.validate("disabled")).thenReturn(true);
        when(jwtUtil.getUsername("disabled")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("disabled")).thenReturn(3);
        when(userMapper.selectOne(any())).thenReturn(user(0, 4));

        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer disabled"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staleTokenVersionGets401OnTheNextRequest() throws Exception {
        when(jwtUtil.validate("stale")).thenReturn(true);
        when(jwtUtil.getUsername("stale")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("stale")).thenReturn(2);
        when(userMapper.selectOne(any())).thenReturn(user(1, 3));

        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer stale"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enabledTokenWithCurrentVersionIsAccepted() throws Exception {
        when(jwtUtil.validate("ok")).thenReturn(true);
        when(jwtUtil.getUsername("ok")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("ok")).thenReturn(3);
        when(userMapper.selectOne(any())).thenReturn(user(1, 3));
        when(deviceService.list(1, 10, null, null)).thenReturn(PageResult.of(0, List.of()));

        mockMvc.perform(get("/api/devices").header("Authorization", "Bearer ok"))
                .andExpect(status().isOk());
    }
}
