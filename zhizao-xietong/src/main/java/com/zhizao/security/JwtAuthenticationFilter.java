package com.zhizao.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhizao.entity.SysUser;
import com.zhizao.mapper.SysUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器：解析 Authorization 头，注入 SecurityContext。
 *
 * <p>每次请求都按用户名查询数据库，校验账号是否启用、令牌版本是否与用户当前版本一致，
 * 不使用缓存，也不留生效窗口。因此管理员禁用账号或用户修改密码后，
 * <strong>下一次请求</strong>就会因认证失败返回 401。</p>
 *
 * <p>带 {@code must_change_password=1} 的账号只放行改密、登出与查询自身信息三个接口，
 * 其余请求直接返回 HTTP 403 + 业务码 {@value #MUST_CHANGE_PASSWORD_CODE}，
 * 前端据此跳转改密页。</p>
 *
 * @author Caspian
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 必须先改密的业务码，前端据此跳转改密页。 */
    public static final int MUST_CHANGE_PASSWORD_CODE = 40301;

    /** 强制改密期间仍可访问的接口。 */
    private static final Set<String> ALLOWED_WHILE_MUST_CHANGE = Set.of(
            "/api/users/me/password", "/api/users/me", "/api/auth/logout");

    private final JwtUtil jwtUtil;
    private final SysUserMapper userMapper;

    /**
     * 构造认证过滤器。
     *
     * @param jwtUtil    令牌工具
     * @param userMapper 用户数据访问，用于逐请求校验启停与令牌版本
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        SysUser authenticated = null;
        if (header != null && header.startsWith("Bearer ")) {
            authenticated = authenticate(header.substring(7));
        }
        if (authenticated != null && Integer.valueOf(1).equals(authenticated.getMustChangePassword())
                && !allowedWhileMustChange(request)) {
            writeMustChangePassword(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 校验令牌并注入认证信息；不通过返回 null，由安全链返回 401。 */
    private SysUser authenticate(String token) {
        if (!jwtUtil.validate(token)) {
            return null;
        }
        String username = jwtUtil.getUsername(token);
        Integer tokenVersion = jwtUtil.getTokenVersion(token);
        if (username == null || tokenVersion == null) {
            return null;
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            return null;
        }
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion != currentVersion) {
            return null;
        }
        UserPrincipal principal = new UserPrincipal(user.getUsername(), user.getRole(), user.getWorkshopScope());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return user;
    }

    /** 强制改密期间仅放行改密、登出与自身信息三个接口。 */
    private boolean allowedWhileMustChange(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path != null && ALLOWED_WHILE_MUST_CHANGE.contains(path);
    }

    /** 输出强制改密的 403 响应，携带可识别业务码。 */
    private void writeMustChangePassword(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + MUST_CHANGE_PASSWORD_CODE
                + ",\"message\":\"请先修改初始密码后再访问\"}");
    }
}
