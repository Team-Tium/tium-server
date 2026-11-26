package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.feedback.FeedbackTestData;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.*;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class FeedbackEvaluationConverterTest {
    @Test void GOOD의_선택적_제안도_등급과_문제목록을_바꾸지_않고_저장하고_조회한다() {
        var base = FeedbackTestData.evaluation(FeedbackTestData.input(false));
        var alternatives = List.of(
                new Alternative(List.of("chat:2"), "일정 확인", "더 확인하고 싶다면 가능한 시간도 물어볼 수 있어요.", "일정 구체화"),
                new Alternative(List.of("chat:2"), "대화 이어가기", "이야기를 이어가고 싶다면 다른 계획도 물어볼 수 있어요.", "대화 확장"));
        var evaluation = new ConversationFeedbackEvaluation(base.targetSpeaker(), base.overallQuality(), base.gradeReason(),
                base.essentialContextMissing(), base.uncertaintyReason(), base.contextUnderstanding(),
                base.responseAssessments(), base.feedback(), base.strengths(), alternatives, base.uncertainties());

        var response = FeedbackConverter.fromEvaluation(evaluation, null);
        var saved = FeedbackConverter.toResponse(FeedbackConverter.toFeedback(1L, 10L, response));

        assertThat(saved).isEqualTo(response);
        assertThat(saved.overallQuality()).isEqualTo("GOOD");
        assertThat(saved.overallFeedback()).isEqualTo(base.feedback());
        assertThat(saved.flowProblem()).isEmpty();
        assertThat(saved.practicePoint()).isEqualTo(alternatives.getFirst().suggestion());
        assertThat(saved.conversationPoints()).containsExactly(alternatives.getLast().suggestion());
    }

    @Test void 사용자용_피드백과_등급_강점을_그대로_기존_응답에_넣는다() {
        var input = FeedbackTestData.input(false); var evaluation = FeedbackTestData.evaluation(input);
        var result = FeedbackConverter.fromEvaluation(evaluation, null);
        assertThat(result.overallQuality()).isEqualTo("GOOD");
        assertThat(result.overallFeedback()).isEqualTo(evaluation.feedback());
        assertThat(result.strength()).containsExactly(evaluation.strengths().getFirst().text());
        assertThat(result.flowProblem()).isEmpty(); assertThat(result.practicePoint()).isEmpty();
        assertThat(result.conversationPoints()).isEmpty();
    }
    @Test void 통화_범위를_표시하고_원래_사용자용_피드백을_보존한다() {
        var input = FeedbackTestData.input(true); var evaluation = FeedbackTestData.evaluation(input);
        var result = FeedbackConverter.fromEvaluation(evaluation, input.scope());
        assertThat(result.overallFeedback()).startsWith("통화의 최근 최대 200개 발화를 기준으로 살펴봤어요.")
                .endsWith(evaluation.feedback());
    }
    @Test void 해소된_MINOR는_문제에서_제외하고_해소된_MAJOR는_유지한다() {
        var input = FeedbackTestData.input(false); var base = FeedbackTestData.evaluation(input);
        var minor = new ResponseAssessment(List.of("chat:2"), List.of("chat:1"), "일정 조율",
                Finding.NEEDS_IMPROVEMENT, Impact.MINOR, true, List.of(), "처음 어긋난 일정은 바꿨어요.");
        var major = new ResponseAssessment(List.of("chat:2"), List.of("chat:1"), "전달 방식",
                Finding.NEEDS_IMPROVEMENT, Impact.MAJOR, true, List.of(), "상대를 깎아내리는 표현이 있었어요.");
        var alternatives = List.of(new Alternative(List.of("chat:2"), "일정 전달", "다른 표현이에요.", "이유"),
                new Alternative(List.of("chat:2"), "입장 전달", "두 번째 표현이에요.", "이유"));
        var evaluation = new ConversationFeedbackEvaluation(base.targetSpeaker(), OverallQuality.BAD, base.gradeReason(),
                false, "", base.contextUnderstanding(), List.of(minor, major), base.feedback(), base.strengths(), alternatives, List.of());
        var result = FeedbackConverter.fromEvaluation(evaluation, null);
        assertThat(result.flowProblem()).containsExactly(major.reason());
        assertThat(result.practicePoint()).isEqualTo(alternatives.getFirst().suggestion());
        assertThat(result.conversationPoints()).containsExactly(alternatives.getLast().suggestion());
    }
    @Test void 판정보류를_BAD로_바꾸지_않고_없던_교정도_만들지_않는다() {
        var base = FeedbackTestData.evaluation(FeedbackTestData.input(false));
        var evaluation = new ConversationFeedbackEvaluation(base.targetSpeaker(), OverallQuality.AMBIGUOUS,
                "핵심 맥락 부족", true, "앞선 대화 필요", "짧은 구간", List.of(),
                "앞서 어떤 이야기를 나눴는지 더 알아야 판단할 수 있어요.", List.of(), List.of(), List.of("맥락 부족"));
        var result = FeedbackConverter.fromEvaluation(evaluation, null);
        assertThat(result.overallQuality()).isEqualTo("AMBIGUOUS"); assertThat(result.flowProblem()).isEmpty();
        assertThat(result.practicePoint()).isEmpty(); assertThat(result.overallFeedback()).isEqualTo(evaluation.feedback());
    }
}
