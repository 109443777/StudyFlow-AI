package com.studyflow.ai.common.auth;

import com.studyflow.ai.common.exception.BusinessException;
import com.studyflow.ai.config.JwtProperties;
import com.studyflow.ai.enums.ResultCodeEnum;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return LoginUser.builder()
                    .userId(Long.parseLong(claims.getSubject()))
                    .username(claims.get("username", String.class))
                    .build();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ResultCodeEnum.TOKEN_INVALID);
        }
    }

    public String resolveToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String tokenPrefix = jwtProperties.getTokenPrefix();
        if (!StringUtils.hasText(tokenPrefix)) {
            return authorizationHeader.trim();
        }
        if (!authorizationHeader.startsWith(tokenPrefix)) {
            return null;
        }
        return authorizationHeader.substring(tokenPrefix.length()).trim();
    }

    public long getExpireSeconds() {
        return jwtProperties.getExpireSeconds();
    }

    public String getHeaderName() {
        return jwtProperties.getHeaderName();
    }

    public String getTokenPrefix() {
        return jwtProperties.getTokenPrefix();
    }
}
