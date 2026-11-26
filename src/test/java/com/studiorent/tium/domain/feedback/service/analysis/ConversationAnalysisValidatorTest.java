package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.PurposeCandidate;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.RequestAnalysis;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.RequestParty;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.RequestAddressing;
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
                List.of(), List.of(new RequestAnalysis("이야기를 들어주세요.", RequestParty.OTHER, RequestParty.ME,
                        RequestAddressing.DIRECT, "이야기 듣기", List.of("m1"), "현재 청자에게 요청한다.")), List.of());
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
    void 서로_다른_근거라도_같은_목적이면_거절하고_하나로_묶은_분석은_허용한다() {
        String text = "어제 반환을 부탁했어. 오늘 돌려받았어.";
        var source = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME,
                List.of(new Utterance("r1", Speaker.ME, text)));
        var past = new PurposeCandidate(UtterancePurpose.INFORMATION_SHARING,
                "어제 반환을 부탁했어.", "과거에 부탁한 사실을 설명한다.");
        var returned = new PurposeCandidate(UtterancePurpose.INFORMATION_SHARING,
                "오늘 돌려받았어.", "반환이 완료됐음을 설명한다.");
        var duplicate = new UtteranceAnalysis("r1", List.of(past, returned), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> validator.validateResult(source, new ConversationAnalysisResult(
                "과거 부탁과 반환 결과 공유", List.of("r1"), List.of(duplicate), List.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                                .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_PURPOSE));

        var merged = new UtteranceAnalysis("r1", List.of(new PurposeCandidate(
                UtterancePurpose.INFORMATION_SHARING, text, "과거 부탁과 현재 반환 결과를 함께 설명한다.")),
                List.of(), List.of(), List.of());
        assertThatCode(() -> validator.validateResult(source, new ConversationAnalysisResult(
                "과거 부탁과 반환 결과 공유", List.of("r1"), List.of(merged), List.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void 한_발화에_서로_다른_목적이_있으면_복수_후보를_허용한다() {
        String text = "내일 면접이 있어. 긴장돼.";
        var source = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME,
                List.of(new Utterance("r1", Speaker.ME, text)));
        var analysis = new UtteranceAnalysis("r1", List.of(
                new PurposeCandidate(UtterancePurpose.INFORMATION_SHARING, "내일 면접이 있어.", "면접 일정을 알린다."),
                new PurposeCandidate(UtterancePurpose.EMOTION_EXPRESSION, "긴장돼.", "자신의 긴장을 명시한다.")),
                List.of("긴장돼."), List.of(), List.of());
        assertThatCode(() -> validator.validateResult(source, new ConversationAnalysisResult(
                "면접 일정과 긴장 공유", List.of("r1"), List.of(analysis), List.of())))
                .doesNotThrowAnyException();
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

    private void validateRequest(RequestParty requester, RequestParty recipient, RequestAddressing addressing,
                                 List<String> evidenceIds, List<String> uncertainties) {
        String text = "내 얘기도 들어야 하는 거 아냐?";
        var source = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.OTHER,
                List.of(new Utterance("r1", Speaker.OTHER, text)));
        var request = new RequestAnalysis(text, requester, recipient, addressing,
                "화자의 이야기를 듣기", evidenceIds, "앞뒤 맥락을 바탕으로 요청 대상을 설명한다.");
        var utterance = new UtteranceAnalysis("r1", List.of(new PurposeCandidate(
                UtterancePurpose.QUESTION, text, "이야기를 들어야 한다는 요구를 표현한다.")),
                List.of(), List.of(request), uncertainties);
        validator.validateResult(source, new ConversationAnalysisResult("요청 표현의 대상 분석", List.of("r1"),
                List.of(utterance), List.of()));
    }

    @Test
    void 직접_요청은_실제_화자와_현재_청자로_구성한다() {
        assertThatCode(() -> validateRequest(RequestParty.OTHER, RequestParty.ME, RequestAddressing.DIRECT,
                List.of("r1"), List.of())).doesNotThrowAnyException();
    }

    @Test
    void 제삼자를_향한_요구와_제삼자의_요청_인용은_직접_요청과_구분해_허용한다() {
        assertThatCode(() -> validateRequest(RequestParty.OTHER, RequestParty.THIRD_PARTY,
                RequestAddressing.REPORTED_OR_THIRD_PARTY, List.of("r1"), List.of())).doesNotThrowAnyException();
        assertThatCode(() -> validateRequest(RequestParty.THIRD_PARTY, RequestParty.ME,
                RequestAddressing.REPORTED_OR_THIRD_PARTY, List.of("r1"), List.of())).doesNotThrowAnyException();
    }

    @Test
    void 제삼자나_다른_요청_주체를_현재_화자의_직접_요청으로_표시하면_거절한다() {
        for (var parties : List.of(List.of(RequestParty.OTHER, RequestParty.THIRD_PARTY),
                List.of(RequestParty.THIRD_PARTY, RequestParty.ME), List.of(RequestParty.ME, RequestParty.OTHER))) {
            assertThatThrownBy(() -> validateRequest(parties.getFirst(), parties.getLast(), RequestAddressing.DIRECT,
                    List.of("r1"), List.of())).isInstanceOfSatisfying(BusinessException.class,
                    exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                            .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_REQUEST_REFERENCE));
        }
    }

    @Test
    void 요청_대상_불명확은_불확실성을_명시하면_허용한다() {
        assertThatCode(() -> validateRequest(RequestParty.OTHER, RequestParty.UNKNOWN, RequestAddressing.UNCERTAIN,
                List.of("r1"), List.of("요구가 현재 청자와 제삼자 중 누구를 향하는지 불분명하다.")))
                .doesNotThrowAnyException();
    }

    @Test
    void 불명확한_요청을_확정하거나_불확실성_설명을_누락하면_거절한다() {
        assertThatThrownBy(() -> validateRequest(RequestParty.OTHER, RequestParty.UNKNOWN,
                RequestAddressing.REPORTED_OR_THIRD_PARTY, List.of("r1"), List.of("대상 불명확")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> validateRequest(RequestParty.OTHER, RequestParty.UNKNOWN,
                RequestAddressing.UNCERTAIN, List.of("r1"), List.of())).isInstanceOf(BusinessException.class);
    }

    @Test
    void 요청의_없는_근거_ID_중복_근거_ID_현재_발화_누락을_거절한다() {
        for (var ids : List.of(List.of("r1", "missing"), List.of("r1", "r1"), List.of("missing"))) {
            assertThatThrownBy(() -> validateRequest(RequestParty.OTHER, RequestParty.ME,
                    RequestAddressing.DIRECT, ids, List.of())).isInstanceOfSatisfying(BusinessException.class,
                    exception -> org.assertj.core.api.Assertions.assertThat(exception.getBaseCode())
                            .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_REQUEST_REFERENCE));
        }
    }

    @Test
    void requests_필드_누락은_빈_배열로_자동_처리하지_않는다() {
        var missing = new UtteranceAnalysis("m1", emotion().purposeCandidates(), List.of(), null, List.of());
        assertInvalid(result(missing, advice()), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
    }
}
