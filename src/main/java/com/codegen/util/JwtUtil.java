package com.codegen.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours}")
    private Long expireHours;

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
