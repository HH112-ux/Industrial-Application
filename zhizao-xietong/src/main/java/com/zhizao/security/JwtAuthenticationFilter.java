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

/**
 * JWT 认证过滤器：解析 Authorization 头，注入 SecurityContext。
 *
 * <p>每次请求都按用户名查询数据库，校验账号是否启用、令牌版本是否与用户当前版本一致，
 * 不使用缓存，也不留生效窗口。因此管理员禁用账号或用户修改密码后，
 * <strong>下一次请求</strong>就会因认证失败返回 401。</p>
 *
 * @author Caspian
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

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
        if (header != null && header.startsWith("Bearer ")) {
            authenticate(header.substring(7));
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 校验令牌并注入认证信息；任一项不满足都不注入，后续由安全链返回 401。
     *
     * @param token 去掉 Bearer 前缀后的 JWT
     */
    private void authenticate(String token) {
        if (!jwtUtil.validate(token)) {
            return;
        }
        String username = jwtUtil.getUsername(token);
        Integer tokenVersion = jwtUtil.getTokenVersion(token);
        if (username == null || tokenVersion == null) {
            return;
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            return;
        }
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVersion != currentVersion) {
            return;
        }
        UserPrincipal principal = new UserPrincipal(user.getUsername(), user.getRole(), user.getWorkshopScope());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
