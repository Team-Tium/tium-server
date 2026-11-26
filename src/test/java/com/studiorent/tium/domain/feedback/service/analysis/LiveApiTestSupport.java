package com.studiorent.tium.domain.feedback.service.analysis;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** 명시적으로 실행하는 실제 API 테스트와 실험의 모델 설정을 공유한다. */
final class LiveApiTestSupport {

    private LiveApiTestSupport() {
    }

    static LiveModel loadModel() throws Exception {
        String apiKey = loadApiKey();
        assertThat(apiKey).as("환경변수 또는 .env에 OPENAI_API_KEY가 필요합니다.")
                .isNotBlank();

        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("dotenv", loadDotEnv()));
        String modelName = environment.resolveRequiredPlaceholders(
                yaml.getObject().getProperty("spring.ai.openai.chat.model"));
        assertThat(modelName).as("application.yaml의 채팅 모델 설정").isNotBlank();

        // 재시도를 끄고 타임아웃을 제한하여 테스트 호출량을 제한한다.
        var model = OpenAiChatModel.builder().options(OpenAiChatOptions.builder()
                .baseUrl("https://api.openai.com/v1")
                .apiKey(apiKey)
                .model(modelName)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(0)
                .build()).build();

        return new LiveModel(modelName, model);
    }

    record LiveModel(String name, OpenAiChatModel model) {
    }

    private static String loadApiKey() throws Exception {
        String environmentKey = System.getenv("OPENAI_API_KEY");
        if (environmentKey != null && !environmentKey.isBlank()) {
            return environmentKey;
        }

        var properties = loadDotEnv();
        String key = properties.getProperty("OPENAI_API_KEY", "").strip();
        if (key.length() >= 2 && ((key.startsWith("\"") && key.endsWith("\""))
                || (key.startsWith("'") && key.endsWith("'")))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static Properties loadDotEnv() throws Exception {
        var properties = new Properties();
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try (Reader reader = Files.newBufferedReader(envFile, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }
}
