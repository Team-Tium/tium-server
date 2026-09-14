package com.studiorent.tium.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * 허용할 origin 목록.
     *
     * <p>정확히 일치하는 {@code setAllowedOrigins}가 아니라 패턴을 쓴다.
     * Vercel Preview 배포는 배포할 때마다 새 주소를 받기 때문에 고정 목록으로는 감당할 수 없다.
     *
     * <p>브라우저는 {@code www} 유무를 서로 다른 origin으로 보므로 apex도 같이 넣는다.
     * apex는 아직 DNS가 연결돼 있지 않지만, 연결되는 시점에 코드 수정 없이 동작한다.
     */
    private static final List<String> ALLOWED_ORIGIN_PATTERNS = List.of(
            "http://localhost:*",
            "https://www.socialtium.site",
            "https://socialtium.site",
            "https://*.vercel.app"
    );

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // 인증은 Authorization 헤더의 Bearer 토큰으로 하고 쿠키를 쓰지 않으므로 false로 둔다
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
