package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisValidator;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAnalysisInputConverterTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversationAnalysisInputConverter converter = new ConversationAnalysisInputConverter(
            new ConversationAnalysisValidator(FACTORY.getValidator()));

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    private ChatMessage chat(Long id, Long senderId, String text) {
        return ChatMessage.builder().id(id).senderId(senderId).messageType(MessageType.TEXT).content(text).build();
    }

    private CallSttSegment segment(Long id, Long speakerId, long start, int sequence, String text) {
        return CallSttSegment.builder().id(id).callId(10L).speakerUserId(speakerId)
                .sequence(sequence).startedAtMs(start).endedAtMs(start + 100).text(text).build();
    }

    private void assertError(Runnable action, ErrorStatus expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getBaseCode()).isEqualTo(expected));
    }

    @Test
    void 채팅을_시간순으로_정렬하고_저장_ID와_화자_원문을_보존한다() {
        var input = converter.fromChatMessages(1L, RelationShip.STRANGER, List.of(
                chat(20L, 1L, "무엇이 가장 힘드세요?"),
                chat(10L, 2L, "요즘 회사 때문에 힘들어요.\n야근도 많고요.")));

        assertThat(input.relationship()).isEqualTo(RelationShip.STRANGER);
        assertThat(input.targetSpeaker()).isEqualTo(Speaker.ME);
        assertThat(input.messages()).containsExactly(
                new Utterance("chat:10", Speaker.OTHER, "요즘 회사 때문에 힘들어요.\n야근도 많고요."),
                new Utterance("chat:20", Speaker.ME, "무엇이 가장 힘드세요?"));
    }

    @Test
    void 삭제_이미지_시스템_메시지는_제외한다() {
        var deleted = chat(2L, 1L, "삭제된 내용");
        deleted.delete();
        var image = ChatMessage.builder().id(3L).senderId(1L).messageType(MessageType.IMAGE).content("image-url").build();
        var system = ChatMessage.builder().id(4L).messageType(MessageType.SYSTEM).content("입장 안내").build();

        var input = converter.fromChatMessages(1L, RelationShip.FRIEND,
                List.of(chat(1L, 1L, "안녕"), deleted, image, system));

        assertThat(input.relationship()).isEqualTo(RelationShip.FRIEND);
        assertThat(input.messages()).containsExactly(new Utterance("chat:1", Speaker.ME, "안녕"));
    }

    @Test
    void 통화는_화자별_sequence가_아닌_시작시각과_ID로_정렬한다() {
        var input = converter.fromCallSegments(1L, RelationShip.STRANGER, List.of(
                segment(30L, 1L, 2000, 1, "세 번째"),
                segment(20L, 2L, 1000, 2, "두 번째"),
                segment(10L, 1L, 1000, 3, "첫 번째")));

        assertThat(input.messages()).containsExactly(
                new Utterance("stt:10", Speaker.ME, "첫 번째"),
                new Utterance("stt:20", Speaker.OTHER, "두 번째"),
                new Utterance("stt:30", Speaker.ME, "세 번째"));
    }

    @Test
    void 대상_사용자를_바꾸어도_ID와_원문은_같고_화자만_바뀐다() {
        var segments = List.of(segment(10L, 1L, 0, 1, "안녕"), segment(20L, 2L, 500, 1, "반가워"));
        var first = converter.fromCallSegments(1L, RelationShip.FRIEND, segments);
        var second = converter.fromCallSegments(2L, RelationShip.FRIEND, segments);

        assertThat(first.messages()).extracting(Utterance::messageId)
                .containsExactlyElementsOf(second.messages().stream().map(Utterance::messageId).toList());
        assertThat(second.messages()).containsExactly(
                new Utterance("stt:10", Speaker.OTHER, "안녕"),
                new Utterance("stt:20", Speaker.ME, "반가워"));
    }

    @Test
    void 분석할_텍스트가_없으면_기존_빈_대화_오류를_반환한다() {
        assertError(() -> converter.fromChatMessages(1L, RelationShip.STRANGER, List.of()),
                ErrorStatus.FEEDBACK_CONVERSATION_EMPTY);
        assertError(() -> converter.fromCallSegments(1L, RelationShip.STRANGER, List.of()),
                ErrorStatus.FEEDBACK_CONVERSATION_EMPTY);
    }

    @Test
    void 저장_ID나_화자가_없으면_임의로_생성하지_않는다() {
        assertError(() -> converter.fromChatMessages(1L, RelationShip.STRANGER, List.of(chat(null, 1L, "안녕"))),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_ID);
        assertError(() -> converter.fromChatMessages(1L, RelationShip.STRANGER, List.of(chat(1L, null, "안녕"))),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_SPEAKER);
        assertError(() -> converter.fromCallSegments(1L, RelationShip.STRANGER,
                List.of(segment(null, 1L, 0, 1, "안녕"))), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_ID);
    }

    @Test
    void 중복_ID와_공백_원문은_기존_입력_검증기로_거절한다() {
        assertError(() -> converter.fromChatMessages(1L, RelationShip.STRANGER,
                List.of(chat(1L, 1L, "안녕"), chat(1L, 2L, "반가워"))), ErrorStatus.FEEDBACK_ANALYSIS_DUPLICATE_MESSAGE_ID);
        assertError(() -> converter.fromCallSegments(1L, RelationShip.STRANGER,
                List.of(segment(1L, 1L, 0, 1, "  "))), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
    }

    @Test
    void 관계와_대상_화자는_추정하지_않는다() {
        assertError(() -> converter.fromChatMessages(1L, null, List.of(chat(1L, 1L, "안녕"))), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
        assertError(() -> converter.fromChatMessages(1L, RelationShip.STRANGER,
                List.of(chat(1L, 2L, "안녕"))), ErrorStatus.FEEDBACK_ANALYSIS_TARGET_SPEAKER_MISSING);
    }

    @Test
    void 잘못된_시작시각과_대상_사용자_누락을_구분한다() {
        assertError(() -> converter.fromCallSegments(1L, RelationShip.STRANGER,
                List.of(segment(1L, 1L, -1, 1, "안녕"))), ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_TIME);
        var missingTime = CallSttSegment.builder().id(1L).speakerUserId(1L).text("안녕").build();
        assertError(() -> converter.fromCallSegments(1L, RelationShip.STRANGER, List.of(missingTime)),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_TIME);
        assertError(() -> converter.fromChatMessages(null, RelationShip.STRANGER, List.of(chat(1L, 1L, "안녕"))),
                ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
    }
}
