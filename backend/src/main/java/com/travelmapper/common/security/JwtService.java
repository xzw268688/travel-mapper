package com.travelmapper.common.security;

import com.travelmapper.common.api.BusinessException;
import com.travelmapper.common.api.ErrorCode;
import com.travelmapper.common.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("TRAVELMAPPER_JWT_SECRET is required and must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = properties.getSecurity().getJwtTtlSeconds();
    }

    public String issue(Long userId, String role, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public JwtPayload parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Object rawTv = claims.get("tv");
            int tokenVersion = rawTv instanceof Number number ? number.intValue() : -1;
            return new JwtPayload(
                    Long.valueOf(claims.getSubject()),
                    claims.get("role", String.class),
                    tokenVersion);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.JWT_INVALID);
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
