package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIServiceException;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.global.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * .\gradlew.bat liveApiTest 로 실행한다. 합성 대화 한 건을 보내며 API 사용량이 발생한다.
 * DB, Redis 및 애플리케이션 전체 컨텍스트를 실행하지 않는다.
 */
@Tag("live-api")
class ConversationAnalysisLiveApiTest {

    @Test
    void 실제_API에서_분석을_받고_원문_근거를_검증한다() throws Exception {
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

        var input = new ConversationAnalysisInput(
                RelationShip.STRANGER,
                Speaker.ME,
                List.of(
                        new Utterance("m1", Speaker.OTHER, "요즘 회사 일 때문에 너무 스트레스 받아요."),
                        new Utterance("m2", Speaker.ME, "그럼 이직하면 되지 않아요?"),
                        new Utterance("m3", Speaker.OTHER, "그게 쉽게 결정할 수 있는 일은 아니라서요.")
                ));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var objectMapper = new ObjectMapper();
            var service = new ConversationAnalysisService(model,
                    new ConversationAnalysisPromptBuilder(objectMapper),
                    new ConversationAnalysisValidator(factory.getValidator()));

            long start = System.nanoTime();
            ConversationAnalysisResult result;
            try {
                result = service.analyze(input);
            } catch (BusinessException exception) {
                fail("실제 API 분석 실패: model=" + modelName + ", code=" + exception.getBaseCode().getCode());
                return;
            } catch (OpenAIServiceException exception) {
                String code = exception.code().filter(value -> value.matches("[a-z_]{1,80}")).orElse("unknown");
                fail("실제 API 분석 실패: model=" + modelName + ", status=" + exception.statusCode() + ", code=" + code);
                return;
            } catch (Exception exception) {
                // SDK 예외 메시지나 요청 옵션을 출력하지 않아 인증 정보 노출을 방지한다.
                fail("실제 API 분석 실패: " + exception.getClass().getSimpleName());
                return;
            }
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(result.utterances()).extracting(ConversationAnalysisResult.UtteranceAnalysis::messageId)
                    .containsExactly("m1", "m2", "m3");
            System.out.printf("Live analysis: model=%s, elapsedMs=%d, validatedUtterances=%d%n",
                    modelName, elapsedMs, result.utterances().size());
            // 출력 대상은 위에서 직접 작성한 합성 대화의 분석 결과뿐이다.
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

    private String loadApiKey() throws Exception {
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

    private Properties loadDotEnv() throws Exception {
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
