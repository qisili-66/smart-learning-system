package com.smartlearning.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    private final Key signingKey;
    private final long accessExpireTime;
    private final long refreshExpireTime;

    public JwtUtil(@Value("${security.jwt.secret}") String secret,
                   @Value("${security.jwt.access-expire-ms}") long accessExpireTime,
                   @Value("${security.jwt.refresh-expire-ms}") long refreshExpireTime) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessExpireTime = accessExpireTime;
        this.refreshExpireTime = refreshExpireTime;
    }

    public String generateToken(Long userId, Integer role) {
        return generateAccessToken(userId, role);
    }

    public String generateAccessToken(Long userId, Integer role) {
        return generateToken(userId, role, ACCESS_TOKEN_TYPE, accessExpireTime);
    }

    public String generateRefreshToken(Long userId, Integer role) {
        return generateToken(userId, role, REFRESH_TOKEN_TYPE, refreshExpireTime);
    }

    private String generateToken(Long userId, Integer role, String tokenType, long expireTime) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireTime))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        return validateToken(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN_TYPE);
    }

    private boolean validateToken(String token, String expectedType) {
        Claims claims = parseToken(token);
        return claims != null
                && claims.getExpiration().after(new Date())
                && expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    public Integer getRole(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("role", Integer.class);
    }
}
