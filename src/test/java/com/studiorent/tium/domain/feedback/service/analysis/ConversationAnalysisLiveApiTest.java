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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static com.studiorent.tium.domain.feedback.service.analysis.LiveApiTestSupport.loadModel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * .\gradlew.bat liveApiTest --tests '*ConversationAnalysisLiveApiTest' 로 실행하며 API 사용량이 발생한다.
 * 분석 응답의 구조와 원문 근거를 검증한다. DB와 Redis는 사용하지 않는다.
 */
@Tag("live-api")
class ConversationAnalysisLiveApiTest {

    @Test
    void 실제_API에서_분석을_받고_원문_근거를_검증한다() throws Exception {
        var configuration = loadModel();
        var model = configuration.model();
        String modelName = configuration.name();

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
                fail("실제 API 분석 실패: " + exception.getClass().getSimpleName());
                return;
            }
            long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertThat(result.utterances()).extracting(ConversationAnalysisResult.UtteranceAnalysis::messageId)
                    .containsExactly("m1", "m2", "m3");
            System.out.printf("Live analysis: model=%s, elapsedMs=%d, validatedUtterances=%d%n",
                    modelName, elapsedMs, result.utterances().size());
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }

}
