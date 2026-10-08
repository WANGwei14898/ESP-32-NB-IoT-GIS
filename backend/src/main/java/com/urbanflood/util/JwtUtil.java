package com.urbanflood.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类，负责登录令牌的生成与解析。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret:urban-flood-monitor-secret-key-please-change-2026}")
    private String secret;

    @Value("${jwt.expire-minutes:1440}")
    private long expireMinutes;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成令牌。 */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + expireMinutes * 60_000L);
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expire)
                .signWith(key())
                .compact();
    }

    /** 解析令牌，失败返回 null。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从令牌中取出用户名。 */
    public String getUsername(String token) {
        Claims claims = parse(token);
        return claims == null ? null : claims.getSubject();
    }
}
