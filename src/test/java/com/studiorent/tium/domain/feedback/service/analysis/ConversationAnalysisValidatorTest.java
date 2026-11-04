package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.PurposeCandidate;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;
import com.studiorent.tium.domain.feedback.dto.analysis.UtterancePurpose;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAnalysisValidatorTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversationAnalysisValidator validator = new ConversationAnalysisValidator(FACTORY.getValidator());

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private ConversationAnalysisInput input() {
        return new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME, List.of(
                new Utterance("m1", Speaker.OTHER, "회사 때문에 너무 힘들어요."),
                new Utterance("m2", Speaker.ME, "그럼 이직하면 되지 않아요?")
        ));
    }

    private UtteranceAnalysis emotion() {
        return new UtteranceAnalysis("m1", List.of(new PurposeCandidate(
                UtterancePurpose.EMOTION_EXPRESSION, "너무 힘들어요.", "힘든 감정을 표현한다.")),
                List.of("너무 힘들어요."), List.of(), List.of("조언을 원하는지는 명시되지 않았다."));
    }

    private UtteranceAnalysis advice() {
        return new UtteranceAnalysis("m2", List.of(new PurposeCandidate(
                UtterancePurpose.ADVICE_OR_SUGGESTION, "이직하면 되지 않아요?", "이직을 해결책으로 제안한다.")),
                List.of(), List.of(), List.of());
    }

    private ConversationAnalysisResult result(UtteranceAnalysis... utterances) {
        return new ConversationAnalysisResult("회사로 인한 어려움을 말하자 이직을 제안했다.",
                List.of("m1", "m2"), Arrays.asList(utterances), List.of());
    }

    private void assertInvalid(ConversationAnalysisResult result, ErrorStatus expectedStatus) {
        assertThatThrownBy(() -> validator.validateResult(input(), result))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                                .isEqualTo(expectedStatus));
    }

    @Test
    void 원문과_일치하는_분석과_빈_선택_목록은_허용한다() {
        assertThatCode(() -> validator.validateResult(input(), result(emotion(), advice())))
                .doesNotThrowAnyException();
    }

    @Test
    void 입력의_중복_ID와_분석_대상_부재를_거절한다() {
        var duplicate = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME, List.of(
                new Utterance("m1", Speaker.ME, "안녕하세요."),
                new Utterance("m1", Speaker.OTHER, "반가워요.")));
        var noTarget = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME,
                List.of(new Utterance("m1", Speaker.OTHER, "안녕하세요.")));
        assertThatThrownBy(() -> validator.validateInput(duplicate))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                                .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_MESSAGE_ID));
        assertThatThrownBy(() -> validator.validateInput(noTarget))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                                .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_TARGET_SPEAKER_MISSING));
    }

    @Test
    void 발화_누락_중복_순서변경_존재하지_않는_ID를_거절한다() {
        assertInvalid(result(emotion()), ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_COUNT_MISMATCH);
        assertInvalid(result(emotion(), emotion()), ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH);
        assertInvalid(result(advice(), emotion()), ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH);
        var unknownId = new UtteranceAnalysis("m99", advice().purposeCandidates(), List.of(), List.of(), List.of());
        assertInvalid(result(emotion(), unknownId), ErrorStatus.FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH);
    }

    @Test
    void 다른_화자의_원문을_목적_근거로_사용하면_거절한다() {
        var wrongQuote = new UtteranceAnalysis("m2", emotion().purposeCandidates(), List.of(), List.of(), List.of());
        assertInvalid(result(emotion(), wrongQuote), ErrorStatus.FEEDBACK_ANALYSIS_PURPOSE_QUOTE_NOT_FOUND);
    }

    @Test
    void 감정이나_요청_인용을_만들어내면_거절한다() {
        var inventedEmotion = new UtteranceAnalysis("m1", emotion().purposeCandidates(),
                List.of("너무 슬퍼요."), List.of(), List.of());
        var inventedRequest = new UtteranceAnalysis("m1", emotion().purposeCandidates(),
                List.of(), List.of("이야기를 들어주세요."), List.of());
        assertInvalid(result(inventedEmotion, advice()), ErrorStatus.FEEDBACK_ANALYSIS_EMOTION_QUOTE_NOT_FOUND);
        assertInvalid(result(inventedRequest, advice()), ErrorStatus.FEEDBACK_ANALYSIS_REQUEST_QUOTE_NOT_FOUND);
    }

    @Test
    void 없는_맥락_근거_ID와_중복_ID를_거절한다() {
        assertInvalid(new ConversationAnalysisResult("맥락 요약", List.of("m99"),
                List.of(emotion(), advice()), List.of()), ErrorStatus.FEEDBACK_ANALYSIS_CONTEXT_EVIDENCE_ID_NOT_FOUND);
        assertInvalid(new ConversationAnalysisResult("맥락 요약", List.of("m1", "m1"),
                List.of(emotion(), advice()), List.of()), ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_CONTEXT_EVIDENCE_ID);
    }

    @Test
    void 중첩된_목적_누락과_null_항목을_거절한다() {
        var missingPurpose = new UtteranceAnalysis("m2", List.of(new PurposeCandidate(
                null, "이직", "목적 누락")), List.of(), List.of(), List.of());
        assertInvalid(result(emotion(), missingPurpose), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
        assertInvalid(result(emotion(), null), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
        assertInvalid(null, ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
    }

    @Test
    void UNKNOWN은_단독이며_불확실성_설명이_있어야_한다() {
        var unknown = new PurposeCandidate(UtterancePurpose.UNKNOWN, "이직", "의도를 확정할 근거가 부족하다.");
        var valid = new UtteranceAnalysis("m2", List.of(unknown), List.of(), List.of(), List.of("의도가 불명확하다."));
        assertThatCode(() -> validator.validateResult(input(), result(emotion(), valid)))
                .doesNotThrowAnyException();
        assertInvalid(result(emotion(), new UtteranceAnalysis("m2", List.of(unknown), List.of(), List.of(), List.of())),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_UNKNOWN_USAGE);
        assertInvalid(result(emotion(), new UtteranceAnalysis("m2",
                List.of(unknown, advice().purposeCandidates().getFirst()), List.of(), List.of(), List.of("불확실"))),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_UNKNOWN_USAGE);
    }

    @Test
    void 같은_목적의_중복_후보를_거절한다() {
        var duplicate = new UtteranceAnalysis("m2",
                List.of(advice().purposeCandidates().getFirst(), advice().purposeCandidates().getFirst()),
                List.of(), List.of(), List.of());
        assertInvalid(result(emotion(), duplicate), ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_PURPOSE);
    }

    @Test
    void 명시한_범위보다_많은_발화를_입력하면_거절한다() {
        var original = input();
        for (int limit : List.of(0, 1)) {
            var oversized = new ConversationAnalysisInput(original.relationship(), original.targetSpeaker(),
                    original.messages(), new ConversationAnalysisInput.Scope(limit, "최근 발화만 분석"));
            assertThatThrownBy(() -> validator.validateInput(oversized))
                    .isInstanceOfSatisfying(BusinessException.class,
                            exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                                    .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT));
        }
    }

    @Test
    void 범위_이하의_발화와_정상_분석은_허용한다() {
        var original = input();
        var scoped = new ConversationAnalysisInput(original.relationship(), original.targetSpeaker(),
                original.messages(), new ConversationAnalysisInput.Scope(200, "통화 최근 최대 200개 발화"));
        assertThatCode(() -> validator.validateResult(scoped, result(emotion(), advice())))
                .doesNotThrowAnyException();
    }
}
