package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.*;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationFeedbackEvaluationValidatorTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversationFeedbackEvaluationValidator validator = new ConversationFeedbackEvaluationValidator(FACTORY.getValidator());
    private final List<Utterance> source = List.of(new Utterance("m1", Speaker.OTHER, "일이 힘들어."),
            new Utterance("m2", Speaker.ME, "상황을 더 이야기해줄래?"));
    private final Evidence evidence = new Evidence("m2", "상황을 더 이야기해줄래?");
    private final Utterance followUp = new Utterance("m0", Speaker.OTHER, "이제 이해했어.");
    private final Evidence followUpEvidence = new Evidence(followUp.messageId(), followUp.text());
    private final List<Utterance> sourceWithFollowUp = List.of(source.getFirst(), source.getLast(), followUp);

    @AfterAll
    static void closeFactory() { FACTORY.close(); }

    private ResponseAssessment assessment(Finding finding, Impact impact, boolean resolved) {
        return assessment(finding, impact, resolved, List.of(evidence));
    }

    private ResponseAssessment assessment(Finding finding, Impact impact, boolean resolved, List<Evidence> evidence) {
        return new ResponseAssessment(List.of("m2"), List.of("m1"), "상황 확인", finding, impact, resolved,
                evidence, "상대가 말한 어려움의 상황을 확인한다.");
    }

    private ConversationFeedbackEvaluation result(OverallQuality grade, List<ResponseAssessment> items,
                                                  boolean missing, List<Strength> strengths, List<Alternative> alternatives) {
        return new ConversationFeedbackEvaluation(Speaker.ME, grade, "개별 평가에 근거한 등급 설명", missing,
                missing ? "전체 판단에 필요한 앞선 상황이 없다." : "", "상대의 어려움에 반응한다.", items,
                "상대 상황을 알아보려는 반응이다.", strengths, alternatives, List.of());
    }

    private void valid(ConversationFeedbackEvaluation result) {
        valid(source, result);
    }

    private void valid(List<Utterance> source, ConversationFeedbackEvaluation result) {
        assertThatCode(() -> validator.validateResult(source, Speaker.ME, result)).doesNotThrowAnyException();
    }

    private void invalid(ConversationFeedbackEvaluation result) {
        invalid(source, result);
    }

    private void invalid(List<Utterance> source, ConversationFeedbackEvaluation result) {
        assertThatThrownBy(() -> validator.validateResult(source, Speaker.ME, result))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getBaseCode())
                        .isEqualTo(ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE));
    }

    @Test
    void 선택적_대안만으로_GOOD을_거절하지_않는다() {
        valid(result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.NONE, false)), false,
                List.of(new Strength("상황을 확인했다.", List.of(evidence))),
                List.of(new Alternative(List.of("m2"), "상황을 확인", "어떤 일이 있었어?", "선택적 표현"))));
    }

    @Test
    void 관련없는_불확실성이_있어도_GOOD은_가능하다() {
        var base = result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.NONE, false)), false, List.of(), List.of());
        valid(new ConversationFeedbackEvaluation(base.targetSpeaker(), base.overallQuality(), base.gradeReason(), false,
                "", base.contextUnderstanding(), base.responseAssessments(), base.feedback(), List.of(), List.of(),
                List.of("대화 밖의 사건은 확인할 수 없다.")));
    }

    @Test
    void 미해결_MINOR가_있으면_GOOD은_거절하고_AMBIGUOUS는_허용한다() {
        var items = List.of(assessment(Finding.HELPFUL, Impact.NONE, false), assessment(Finding.NEEDS_IMPROVEMENT, Impact.MINOR, false));
        invalid(result(OverallQuality.GOOD, items, false, List.of(), List.of()));
        valid(result(OverallQuality.AMBIGUOUS, items, false, List.of(), List.of()));
    }

    @Test
    void 핵심_정보가_부족하면_AMBIGUOUS를_허용한다() {
        valid(result(OverallQuality.AMBIGUOUS, List.of(assessment(Finding.UNCERTAIN, Impact.NONE, false)), true, List.of(), List.of()));
        invalid(result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.NONE, false)), true, List.of(), List.of()));
    }

    @ParameterizedTest
    @EnumSource(OverallQuality.class)
    void MAJOR가_확인되면_BAD만_허용한다(OverallQuality grade) {
        var value = result(grade, List.of(assessment(Finding.NEEDS_IMPROVEMENT, Impact.MAJOR, true,
                List.of(evidence, followUpEvidence))), false, List.of(), List.of());
        if (grade == OverallQuality.BAD) valid(sourceWithFollowUp, value); else invalid(sourceWithFollowUp, value);
    }

    @Test
    void 도움이_된_반응만_있으면_BAD와_AMBIGUOUS는_모순이다() {
        var items = List.of(assessment(Finding.HELPFUL, Impact.NONE, false));
        invalid(result(OverallQuality.BAD, items, false, List.of(), List.of()));
        invalid(result(OverallQuality.AMBIGUOUS, items, false, List.of(), List.of()));
    }

    @ParameterizedTest
    @EnumSource(Speaker.class)
    void 해소된_MINOR는_ID가_작아도_원문_순서상_뒤인_근거가_있으면_GOOD과_함께_허용한다(Speaker speaker) {
        var extended = List.of(source.getFirst(), source.getLast(), new Utterance("m0", speaker, followUp.text()));
        valid(extended, result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.NONE, false),
                assessment(Finding.NEEDS_IMPROVEMENT, Impact.MINOR, true, List.of(evidence, followUpEvidence))),
                false, List.of(), List.of()));
    }

    @ParameterizedTest
    @EnumSource(value = Impact.class, names = {"MINOR", "MAJOR"})
    void 해소_근거가_대상_발화와_이전_발화뿐이면_거절한다(Impact impact) {
        var items = List.of(assessment(Finding.HELPFUL, Impact.NONE, false),
                assessment(Finding.NEEDS_IMPROVEMENT, impact, true,
                        List.of(new Evidence("m1", "일이 힘들어."), evidence)));
        invalid(result(impact == Impact.MAJOR ? OverallQuality.BAD : OverallQuality.GOOD,
                items, false, List.of(), List.of()));
    }

    @Test
    void 후속_발화가_존재해도_해소_근거에_포함하지_않으면_거절한다() {
        invalid(sourceWithFollowUp, result(OverallQuality.GOOD,
                List.of(assessment(Finding.HELPFUL, Impact.NONE, false),
                        assessment(Finding.NEEDS_IMPROVEMENT, Impact.MINOR, true)), false, List.of(), List.of()));
    }

    @Test
    void 복수_대상_중_마지막_발화_이전의_근거만으로_해소를_인정하지_않는다() {
        var extended = List.of(source.getFirst(), source.getLast(), new Utterance("m0", Speaker.OTHER, "이제 이해했어."),
                new Utterance("m3", Speaker.ME, "아직 궁금한 게 있어."));
        var resolved = new ResponseAssessment(List.of("m3", "m2"), List.of("m1"), "상황 확인",
                Finding.NEEDS_IMPROVEMENT, Impact.MINOR, true,
                List.of(evidence, new Evidence("m0", "이제 이해했어."), new Evidence("m3", "아직 궁금한 게 있어.")), "해소됐다고 주장한다.");
        invalid(extended, result(OverallQuality.GOOD,
                List.of(assessment(Finding.HELPFUL, Impact.NONE, false), resolved), false, List.of(), List.of()));
    }

    @Test
    void 복수_대상의_마지막_발화보다_뒤인_해소_근거가_있으면_허용한다() {
        var lastTarget = new Utterance("m3", Speaker.ME, "다시 설명할게.");
        var extended = List.of(source.getFirst(), source.getLast(), lastTarget, followUp);
        var resolved = new ResponseAssessment(List.of("m3", "m2"), List.of("m1"), "상황 확인",
                Finding.NEEDS_IMPROVEMENT, Impact.MINOR, true,
                List.of(evidence, new Evidence(lastTarget.messageId(), lastTarget.text()), followUpEvidence), "후속 반응에서 확인한다.");

        valid(extended, result(OverallQuality.GOOD,
                List.of(assessment(Finding.HELPFUL, Impact.NONE, false), resolved), false, List.of(), List.of()));
    }

    @Test
    void 후속_근거가_있어도_resolved가_false면_MINOR를_자동_해소하지_않는다() {
        var items = List.of(assessment(Finding.HELPFUL, Impact.NONE, false),
                assessment(Finding.NEEDS_IMPROVEMENT, Impact.MINOR, false, List.of(evidence, followUpEvidence)));

        valid(sourceWithFollowUp, result(OverallQuality.AMBIGUOUS, items, false, List.of(), List.of()));
        invalid(sourceWithFollowUp, result(OverallQuality.GOOD, items, false, List.of(), List.of()));
    }

    @Test
    void 도움이_됐다는_판정없이_장점만_생성하면_거절한다() {
        invalid(result(OverallQuality.AMBIGUOUS, List.of(), true,
                List.of(new Strength("확인되지 않은 장점", List.of(evidence))), List.of()));
    }

    @Test
    void 다른_화자를_대상으로_판정하면_거절한다() {
        var wrong = new ResponseAssessment(List.of("m1"), List.of(), "반응", Finding.HELPFUL, Impact.NONE, false,
                List.of(new Evidence("m1", "일이 힘들어.")), "설명");
        invalid(result(OverallQuality.GOOD, List.of(wrong), false, List.of(), List.of()));
    }

    @Test
    void 잘못된_인용과_존재하지_않는_반응_참조를_거절한다() {
        var quote = new ResponseAssessment(List.of("m2"), List.of("m1"), "반응", Finding.HELPFUL, Impact.NONE, false,
                List.of(new Evidence("m2", "힘들었겠네")), "설명");
        invalid(result(OverallQuality.GOOD, List.of(quote), false, List.of(), List.of()));
        var reference = new ResponseAssessment(List.of("m2"), List.of("missing"), "반응", Finding.HELPFUL, Impact.NONE, false,
                List.of(evidence), "설명");
        invalid(result(OverallQuality.GOOD, List.of(reference), false, List.of(), List.of()));
    }

    @Test
    void 정보부족_설명과_심각도_모순을_거절한다() {
        var base = result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.NONE, false)), false, List.of(), List.of());
        invalid(new ConversationFeedbackEvaluation(base.targetSpeaker(), base.overallQuality(), base.gradeReason(),
                true, "", base.contextUnderstanding(), base.responseAssessments(), base.feedback(), List.of(), List.of(), List.of()));
        invalid(result(OverallQuality.AMBIGUOUS, List.of(assessment(Finding.NEEDS_IMPROVEMENT, Impact.NONE, false)), false, List.of(), List.of()));
        invalid(result(OverallQuality.GOOD, List.of(assessment(Finding.HELPFUL, Impact.MINOR, false)), false, List.of(), List.of()));
    }

    @Test
    void 불확실한_평가만_있고_핵심정보부족도_없으면_등급을_확정하지_못한다() {
        invalid(result(OverallQuality.AMBIGUOUS, List.of(assessment(Finding.UNCERTAIN, Impact.NONE, false)), false, List.of(), List.of()));
    }
}
