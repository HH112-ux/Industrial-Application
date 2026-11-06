package com.zhizao.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：生成与解析令牌。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成令牌，并写入用户当前的令牌版本。
     *
     * <p>令牌版本用于改密或账号启停后让旧令牌立即失效：服务端只接受
     * 版本号与数据库一致的令牌。</p>
     *
     * @param username     用户名
     * @param role         角色
     * @param tokenVersion 用户当前的令牌版本
     * @return 签名后的 JWT
     */
    public String generateToken(String username, String role, int tokenVersion) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
    }

    public String getUsername(String token) {
        return parse(token).getSubject();
    }

    public String getRole(String token) {
        return parse(token).get("role", String.class);
    }

    /**
     * 读取令牌中的令牌版本。
     *
     * @param token JWT
     * @return 令牌版本；旧令牌不含该声明时返回 null
     */
    public Integer getTokenVersion(String token) {
        return parse(token).get("tv", Integer.class);
    }

    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
