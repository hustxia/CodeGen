package com.codegen.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /**
     * application.yml 里的出厂默认值。只为「克隆下来直接能跑」，
     * 一旦用于对外环境，等于把签发权公开。
     */
    private static final String DEV_DEFAULT_SECRET = "codegen-platform-jwt-secret-key-2026-very-long";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private Long expireHours;

    @PostConstruct
    void warnIfSecretIsDefault() {
        if (DEV_DEFAULT_SECRET.equals(secret)) {
            log.warn("jwt.secret 正在使用仓库内置默认值，仅适用于本机开发；"
                    + "部署到任何共享/公网环境前请设置环境变量 JWT_SECRET（否则 token 可被伪造）");
        }
    }

    /**
     * 生成 token（HS256）
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(secretKey())
                .compact();
    }

    /**
     * 解析 token，无效或过期时抛异常
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 取用户 ID。
     *
     * <p>不能用 {@code claims.get("userId", Long.class)}：Jackson 会把小整数反序列化成 Integer，
     * 传 Long.class 时 JJWT 会抛 RequiredTypeException（"Cannot convert existing claim value of
     * type Integer to Long"），被上层兜底成「token 无效」——用户 ID 是 1 这种小数字时必现。
     */
    public Long userIdOf(String token) {
        Object value = parseToken(token).get("userId");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    /**
     * 从 token 取用户名（可能为空）
     */
    public String usernameOf(String token) {
        Object value = parseToken(token).get("username");
        return value == null ? null : String.valueOf(value);
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
