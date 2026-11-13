package com.zhizao.security;

import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * 安全配置：无状态 JWT 鉴权、CORS 与公开端点白名单。
 *
 * @author Caspian
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean deviceAuthEnabled;
    private final boolean apiDocsEnabled;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:8081}")
    private String allowedOrigins;

    /**
     * 构造安全配置。
     *
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param deviceAuthEnabled      是否开启设备 HMAC 鉴权；开启时设备上报端点不走用户令牌
     * @param apiDocsEnabled         是否开放接口文档；默认关闭，关闭时 Swagger 路径不进入白名单
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${device.auth.enabled:false}") boolean deviceAuthEnabled,
                          @Value("${app.api-docs.enabled:false}") boolean apiDocsEnabled) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.deviceAuthEnabled = deviceAuthEnabled;
        this.apiDocsEnabled = apiDocsEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置过滤链：设备鉴权开启时放行设备上报端点，由 HMAC 校验替代用户令牌。
     *
     * <p>接口文档默认不开放；仅在 {@code app.api-docs.enabled=true} 时才把 Swagger 路径加入白名单。
     * Actuator 指标含 JVM 与业务运行态信息，只允许 ADMIN 读取。</p>
     *
     * @param http HTTP 安全构建器
     * @return 安全过滤链
     * @throws Exception 构建失败时抛出
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info").permitAll();
                    if (apiDocsEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                    }
                    if (deviceAuthEnabled) {
                        auth.requestMatchers("/api/integration/device-status").permitAll();
                    }
                    auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
                }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
