package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.FeedbackTestData;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 합성 대화로 실제 Java의 1차→2차→기존 응답 변환을 확인한다. 일반 test/CI에서는 제외한다. */
@Tag("live-api")
class ConversationFeedbackServiceLiveApiTest {
    @Test
    void 합성_대화로_실제_두_단계와_사용자용_응답을_확인한다() throws Exception {
        var live = LiveApiTestSupport.loadModel();
        var mapper = new ObjectMapper();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ConversationAnalysisValidator(factory.getValidator());
            var pipeline = new ConversationFeedbackService(
                    new ConversationAnalysisService(live.model(), new ConversationAnalysisPromptBuilder(mapper), validator),
                    new ConversationFeedbackEvaluationService(live.model(), new ConversationFeedbackEvaluationPromptBuilder(mapper),
                            validator, new ConversationFeedbackEvaluationValidator(factory.getValidator())));
            var input = FeedbackTestData.input(false);
            long start = System.nanoTime();
            var response = pipeline.generate(input);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(response.overallQuality()).isIn("GOOD", "AMBIGUOUS", "BAD");
            assertThat(response.overallFeedback()).isNotBlank().doesNotContain("ME", "OTHER", "MINOR", "MAJOR");
            Path output = Path.of("build/reports/feedback-service-live", "result-" + Instant.now().toEpochMilli() + ".json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "model", live.name(), "input", input, "response", response, "elapsedMs", elapsedMs,
                    "scope", "SYNTHETIC_JAVA_PIPELINE_ONLY", "ragEnabled", false)));
        }
    }
}
