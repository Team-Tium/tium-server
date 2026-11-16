package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.FeedbackTestData;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Finding;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Impact;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.ResponseAssessment;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Strength;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.Evidence;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
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

    @Test void 원문은_유효하지만_HELPFUL과_연결되지_않은_장점만_제외하고_평가는_보존한다() {
        var source = List.of(
                new Utterance("m1", Speaker.OTHER, "자료는 내일 오전에 보낼 수 있어."),
                new Utterance("m2", Speaker.ME, "좋아, 내일 오전에 보내줘."),
                new Utterance("m3", Speaker.ME, "이 부분은 나중에 더 이야기하자."));
        var helpfulEvidence = new Evidence("m2", "내일 오전에 보내줘.");
        var uncertainEvidence = new Evidence("m3", "나중에 더 이야기하자.");
        var linked = new Strength("가능한 일정에 맞춰 요청했어요.", List.of(helpfulEvidence));
        var unlinked = new Strength("추가 대화를 제안했어요.", List.of(uncertainEvidence));
        var raw = new ConversationFeedbackEvaluation(Speaker.ME, OverallQuality.GOOD,
                "상대가 가능한 일정에 맞춰 응답했어요.", false, "", "자료 전달 일정을 조율한다.",
                List.of(
                        new ResponseAssessment(List.of("m2"), List.of("m1"), "일정 조율", Finding.HELPFUL,
                                Impact.NONE, false, List.of(helpfulEvidence), "상대가 제시한 시간을 반영했어요."),
                        new ResponseAssessment(List.of("m3"), List.of(), "추가 대화 제안", Finding.UNCERTAIN,
                                Impact.NONE, false, List.of(uncertainEvidence), "추가로 논의할 내용은 드러나지 않았어요.")),
                "상대가 가능한 시간에 맞춰 자료 전달을 요청했어요.", List.of(linked, unlinked), List.of(),
                List.of("추가 대화의 주제는 명시되지 않았어요."));

        assertThatThrownBy(() -> evaluationValidator.validateResult(source, Speaker.ME, raw))
                .isInstanceOf(BusinessException.class);

        var recovered = evaluationValidator.validateAndFilterStrengths(source, Speaker.ME, raw);

        assertThat(recovered).isEqualTo(withStrengths(raw, List.of(linked)));
        assertThat(recovered.responseAssessments()).isSameAs(raw.responseAssessments());
        assertThat(raw.strengths()).containsExactly(linked, unlinked);
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
