package com.studiorent.tium.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tium API")
                        .description("낯선 사람과 대화를 연습하고 AI 피드백으로 화술을 개선하는 서비스")
                        .version("v1"))
                .servers(List.of(new Server().url("/").description("current")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("로그인 응답의 accessToken을 그대로 넣으세요. 'Bearer ' 접두어는 자동으로 붙습니다.");
    }
}
