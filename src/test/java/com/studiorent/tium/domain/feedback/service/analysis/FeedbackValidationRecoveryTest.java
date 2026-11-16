package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.FeedbackTestData;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Strength;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Evidence;
import com.studiorent.tium.global.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FeedbackValidationRecoveryTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversationAnalysisValidator analysisValidator = new ConversationAnalysisValidator(FACTORY.getValidator());
    private final ConversationFeedbackEvaluationValidator evaluationValidator = new ConversationFeedbackEvaluationValidator(FACTORY.getValidator());
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterAll static void close() { FACTORY.close(); }

    @Test void 실제_원문이_아닌_장점_인용은_제외로_숨기지_않고_거절한다() {
        var input = FeedbackTestData.input(false);
        var base = FeedbackTestData.evaluation(input);
        var invalid = withStrengths(base, List.of(new Strength("잘했어요.", List.of(new Evidence("chat:2", "없는 문장")))));
        assertThatThrownBy(() -> evaluationValidator.validateAndFilterStrengths(input.messages(), input.targetSpeaker(), invalid))
                .isInstanceOf(BusinessException.class);
    }

    @Test void 장점_정리후에도_등급_모순은_거절한다() {
        var input = FeedbackTestData.input(false);
        var base = FeedbackTestData.evaluation(input);
        var invalid = new ConversationFeedbackEvaluation(base.targetSpeaker(), base.overallQuality(), base.gradeReason(),
                false, "", base.contextUnderstanding(), List.of(), base.feedback(), base.strengths(), List.of(), List.of());
        assertThatThrownBy(() -> evaluationValidator.validateAndFilterStrengths(input.messages(), input.targetSpeaker(), invalid))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FEEDBACK_VALIDATION_REPLAY_ROOT", matches = ".+")
    void 중단된_실제_3건을_API없이_재생한다() throws Exception {
        Path root = Path.of(System.getenv("FEEDBACK_VALIDATION_REPLAY_ROOT"));
        var cases = mapper.readTree(Files.readString(root.resolve("inputs-snapshot.json"))).get("cases");
        for (String caseId : List.of("C04", "C05", "C09")) {
            var item = java.util.stream.StreamSupport.stream(cases.spliterator(), false)
                    .filter(node -> node.get("caseId").asText().equals(caseId)).findFirst().orElseThrow();
            var input = mapper.treeToValue(item.get("conversation"), ConversationAnalysisInput.class);
            if (!caseId.equals("C09")) {
                var raw = mapper.readValue(Files.readString(root.resolve(caseId + "/service/call-1-raw.txt")), ConversationAnalysisResult.class);
                var recovered = ConversationAnalysisEvidence.bindOriginalUtterances(input.messages(), raw);
                analysisValidator.validateResult(input, recovered);
                assertThat(recovered.utterances()).hasSameSizeAs(raw.utterances());
                for (int i = 0; i < raw.utterances().size(); i++) {
                    assertThat(recovered.utterances().get(i).requests()).isEqualTo(raw.utterances().get(i).requests());
                    var source = input.messages().get(i).text();
                    assertThat(recovered.utterances().get(i).explicitEmotionQuotes()).isEqualTo(
                            raw.utterances().get(i).explicitEmotionQuotes().stream().filter(source::contains).toList());
                }
            } else {
                var raw = mapper.readValue(Files.readString(root.resolve(caseId + "/service/call-2-raw.txt")), ConversationFeedbackEvaluation.class);
                var recovered = evaluationValidator.validateAndFilterStrengths(input.messages(), input.targetSpeaker(), raw);
                assertThat(recovered.strengths()).hasSize(1);
                assertThat(recovered.overallQuality()).isEqualTo(raw.overallQuality());
                assertThat(recovered.responseAssessments()).isEqualTo(raw.responseAssessments());
                assertThat(recovered.feedback()).isEqualTo(raw.feedback());
            }
        }
    }

    private static ConversationFeedbackEvaluation withStrengths(ConversationFeedbackEvaluation base, List<Strength> strengths) {
        return new ConversationFeedbackEvaluation(base.targetSpeaker(), base.overallQuality(), base.gradeReason(),
                base.essentialContextMissing(), base.uncertaintyReason(), base.contextUnderstanding(), base.responseAssessments(),
                base.feedback(), strengths, base.optionalAlternatives(), base.uncertainties());
    }
}
