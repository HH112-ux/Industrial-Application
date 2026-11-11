package com.zhizao.controller;

import com.zhizao.common.BadRequestException;
import com.zhizao.common.BusinessException;
import com.zhizao.common.ForbiddenException;
import com.zhizao.common.GlobalExceptionHandler;
import com.zhizao.dto.UserCreateResponse;
import com.zhizao.dto.UserView;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import com.zhizao.security.JwtAuthenticationFilter;
import com.zhizao.security.JwtUtil;
import com.zhizao.security.SecurityConfig;
import com.zhizao.service.AuthService;
import com.zhizao.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@ContextConfiguration(classes = UserCreateHttpTest.EmptyConfig.class)
@Import({UserController.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class})
class UserCreateHttpTest {
    @SpringBootConfiguration
    static class EmptyConfig {
    }
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;
    @MockBean
    private AuthService authService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private SysUserMapper userMapper;

    private static final String BODY =
            "{\"username\":\"wangwu\",\"nickname\":\"王五\",\"role\":\"USER\",\"workshopScope\":\"一车间\"}";

    private void authenticateAs(String role) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("operator");
        user.setRole(role);
        user.setEnabled(1);
        user.setTokenVersion(0);
        when(jwtUtil.validate("token")).thenReturn(true);
        when(jwtUtil.getUsername("token")).thenReturn("operator");
        when(jwtUtil.getTokenVersion("token")).thenReturn(0);
        when(userMapper.selectOne(any())).thenReturn(user);
    }

    private org.springframework.test.web.servlet.ResultActions create() throws Exception {
        return mockMvc.perform(post("/api/users").header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    @Test
    void adminCanCreateUserAndSeesInitialPasswordOnce() throws Exception {
        authenticateAs("ADMIN");
        UserView view = new UserView(7L, "wangwu", "王五", "USER", true, true, "一车间",
                "LOCAL", null, LocalDateTime.now());
        when(userService.create(any())).thenReturn(new UserCreateResponse(view, "InitPass1234"));

        create().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("wangwu"))
                .andExpect(jsonPath("$.data.initialPassword").value("InitPass1234"));
    }

    @Test
    void nonAdminCannotCreateUser() throws Exception {
        authenticateAs("USER");
        create().andExpect(status().isForbidden());

        verify(userService, never()).create(any());
    }

    @Test
    void duplicateAndInvalidRoleReturnBusinessCodes() throws Exception {
        authenticateAs("ADMIN");
        when(userService.create(any())).thenThrow(new BusinessException(409, "用户名已存在，请更换后重试"))
                .thenThrow(new BadRequestException("角色只能是 ADMIN、USER 或 WORKER"));

        create().andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在，请更换后重试"));
        create().andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void deletingSelfReturns403() throws Exception {
        authenticateAs("ADMIN");
        doThrow(new ForbiddenException("不能删除当前登录账号")).when(userService).delete(1L, "operator");

        mockMvc.perform(delete("/api/users/1").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void disablingSelfReturns403() throws Exception {
        authenticateAs("ADMIN");
        when(userService.setEnabled(1L, false, "operator"))
                .thenThrow(new ForbiddenException("不能禁用当前登录账号"));

        mockMvc.perform(post("/api/users/1/status").param("enabled", "false")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
