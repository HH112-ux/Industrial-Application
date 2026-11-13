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
 *
 * <p>签名密钥在构造期完成强度校验：HS256 要求密钥不短于 256 位（32 字节）。
 * 校验放在启动阶段，配置过弱时应用直接启动失败并给出明确提示，
 * 而不是等到首次登录签发令牌才抛 {@code WeakKeyException}（表现为登录 500）。</p>
 *
 * @author Caspian
 */
@Component
public class JwtUtil {

    /** HS256 允许的最短密钥长度（字节）。 */
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expiration;

    /**
     * 构造令牌工具，并在启动期校验密钥强度。
     *
     * @param secret     签名密钥，必须至少 32 字节
     * @param expiration 令牌有效期，毫秒
     */
    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration:86400000}") long expiration) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret 强度不足：HS256 至少需要 " + MIN_SECRET_BYTES
                    + " 字节，当前为 " + bytes.length + " 字节。请用 `openssl rand -base64 64` 生成后通过 JWT_SECRET 注入");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expiration = expiration;
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
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
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
