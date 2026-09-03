package com.studiorent.tium.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String TOKEN_TYPE_CLAIM = "type";

    private final JwtProperties jwtProperties;

    private SecretKey key;

    @PostConstruct
    private void init() {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String createAccessToken(Long memberId) {
        return createToken(memberId, TokenType.ACCESS, jwtProperties.accessTokenExpiration());
    }

    public String createRefreshToken(Long memberId) {
        return createToken(memberId, TokenType.REFRESH, jwtProperties.refreshTokenExpiration());
    }

    private String createToken(Long memberId, TokenType tokenType, Duration expiration) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(memberId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType.name())
                .signWith(key)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validate(token, TokenType.ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validate(token, TokenType.REFRESH);
    }

    private boolean validate(String token, TokenType expectedType) {
        try {
            Claims claims = parseClaims(token);
            return hasType(claims, expectedType) && hasNumericSubject(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getMemberId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    private boolean hasType(Claims claims, TokenType expectedType) {
        return expectedType.name().equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    private boolean hasNumericSubject(Claims claims) {
        try {
            Long.valueOf(claims.getSubject());
            return true;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
