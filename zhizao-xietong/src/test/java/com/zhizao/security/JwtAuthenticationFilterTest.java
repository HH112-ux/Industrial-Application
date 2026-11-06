package com.zhizao.security;

import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private SysUserMapper userMapper;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtUtil, userMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SysUser user(int enabled, int tokenVersion, String workshopScope) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("zhangsan");
        user.setRole("USER");
        user.setEnabled(enabled);
        user.setTokenVersion(tokenVersion);
        user.setWorkshopScope(workshopScope);
        return user;
    }

    private void runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/work-orders");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void enabledUserWithCurrentTokenVersionIsAuthenticated() throws Exception {
        when(jwtUtil.validate("t1")).thenReturn(true);
        when(jwtUtil.getUsername("t1")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("t1")).thenReturn(3);
        when(userMapper.selectOne(any())).thenReturn(user(1, 3, "二车间"));

        runFilter("t1");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("zhangsan", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> "ROLE_USER".equals(a.getAuthority())));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertEquals("二车间", principal.workshopScope());
    }

    @Test
    void disabledUserIsNotAuthenticatedOnTheVeryNextRequest() throws Exception {
        when(jwtUtil.validate("t2")).thenReturn(true);
        when(jwtUtil.getUsername("t2")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("t2")).thenReturn(3);
        when(userMapper.selectOne(any())).thenReturn(user(0, 4, "二车间"));

        runFilter("t2");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void staleTokenVersionIsNotAuthenticated() throws Exception {
        when(jwtUtil.validate("t3")).thenReturn(true);
        when(jwtUtil.getUsername("t3")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("t3")).thenReturn(2);
        when(userMapper.selectOne(any())).thenReturn(user(1, 3, "二车间"));

        runFilter("t3");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void tokenWithoutVersionClaimIsRejected() throws Exception {
        when(jwtUtil.validate("t4")).thenReturn(true);
        when(jwtUtil.getUsername("t4")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("t4")).thenReturn(null);

        runFilter("t4");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void unknownUserIsNotAuthenticated() throws Exception {
        when(jwtUtil.validate("t5")).thenReturn(true);
        when(jwtUtil.getUsername("t5")).thenReturn("ghost");
        when(jwtUtil.getTokenVersion("t5")).thenReturn(0);
        when(userMapper.selectOne(any())).thenReturn(null);

        runFilter("t5");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void everyRequestHitsTheDatabaseForEnabledAndVersion() throws Exception {
        when(jwtUtil.validate("t6")).thenReturn(true);
        when(jwtUtil.getUsername("t6")).thenReturn("zhangsan");
        when(jwtUtil.getTokenVersion("t6")).thenReturn(1);
        when(userMapper.selectOne(any())).thenReturn(user(1, 1, "二车间"));

        runFilter("t6");
        runFilter("t6");

        verify(userMapper, org.mockito.Mockito.times(2)).selectOne(any());
    }
}
