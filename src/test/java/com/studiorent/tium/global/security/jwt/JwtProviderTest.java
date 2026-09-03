package com.studiorent.tium.global.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWp3dC1wcm92aWRlci11bml0LXRlc3QtMTIzNDU2Nzg5MA==";

    private JwtProvider provider() throws Exception {
        JwtProperties props = new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
        JwtProvider p = new JwtProvider(props);
        var init = JwtProvider.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(p);
        return p;
    }

    @Test
    void access_토큰은_생성후_검증과_memberId_추출이_된다() throws Exception {
        JwtProvider p = provider();
        String token = p.createAccessToken(42L);

        assertThat(p.validateAccessToken(token)).isTrue();
        assertThat(p.isExpired(token)).isFalse();
        assertThat(p.getMemberId(token)).isEqualTo(42L);
    }

    @Test
    void refresh_토큰은_access_토큰으로_사용할_수_없다() throws Exception {
        JwtProvider p = provider();
        String refreshToken = p.createRefreshToken(42L);

        assertThat(p.validateRefreshToken(refreshToken)).isTrue();
        assertThat(p.validateAccessToken(refreshToken)).isFalse();
    }

    @Test
    void access_토큰은_refresh_토큰으로_사용할_수_없다() throws Exception {
        JwtProvider p = provider();

        assertThat(p.validateRefreshToken(p.createAccessToken(42L))).isFalse();
    }

    @Test
    void 다른_키로_서명된_토큰은_검증에_실패한다() throws Exception {
        JwtProvider p = provider();
        String otherSecret =
                "YW5vdGhlci1zZWNyZXQta2V5LXRoYXQtaXMtY29tcGxldGVseS1kaWZmZXJlbnQtMTIzNDU2Nzg5MA==";
        Instant now = Instant.now();
        String forged = Jwts.builder()
                .subject("42")
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(otherSecret)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .compact();

        assertThat(p.validateAccessToken(forged)).isFalse();
    }

    @Test
    void payload가_변조된_토큰은_검증에_실패한다() throws Exception {
        JwtProvider p = provider();
        String[] parts = p.createAccessToken(42L).split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"type\":\"ACCESS\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(p.validateAccessToken(parts[0] + "." + tamperedPayload + "." + parts[2])).isFalse();
    }

    @Test
    void subject가_숫자가_아니면_검증에_실패한다() throws Exception {
        JwtProvider p = provider();
        Instant now = Instant.now();
        String forged = Jwts.builder()
                .subject("not-a-number")
                .claim("type", "ACCESS")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .compact();

        assertThat(p.validateAccessToken(forged)).isFalse();
    }

    @Test
    void type_claim이_없으면_검증에_실패한다() throws Exception {
        JwtProvider p = provider();
        Instant now = Instant.now();
        String legacy = Jwts.builder()
                .subject("42")
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(600)))
                .compact();

        assertThat(p.validateAccessToken(legacy)).isFalse();
    }
}
