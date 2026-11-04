package com.studiorent.tium.domain.call;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallSttJobRepository;
import com.studiorent.tium.domain.call.repository.CallSttSegmentRepository;
import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.call.service.query.CallSttQueryServiceImpl;
import com.studiorent.tium.domain.feedback.dto.ConversationTurn;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CallSttFeedbackConversationTest {

    private final CallAccessValidator accessValidator = mock(CallAccessValidator.class);
    private final CallSttSegmentRepository repository = mock(CallSttSegmentRepository.class);
    private final CallSttQueryServiceImpl service = new CallSttQueryServiceImpl(
            accessValidator, mock(CallSttJobRepository.class), repository, mock(FeedbackRepository.class));

    private CallSttSegment segment(long id, long speakerId, String text) {
        return CallSttSegment.builder().id(id).callId(10L).speakerUserId(speakerId)
                .sequence(1).startedAtMs(id * 100).endedAtMs(id * 100 + 50).text(text).build();
    }

    private void assertError(ErrorStatus expected) {
        assertThatThrownBy(() -> service.findSegmentsForFeedback(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getBaseCode()).isEqualTo(expected));
    }

    @Test
    void 권한_실패시_STT를_조회하지_않는다() {
        var forbidden = new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        when(accessValidator.validateParticipant(10L, 1L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.findSegmentsForFeedback(1L, 10L)).isSameAs(forbidden);
        verifyNoInteractions(repository);
    }

    @Test
    void STT가_없으면_기존_없음_오류를_유지한다() {
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L)).thenReturn(List.of());

        assertError(ErrorStatus.CALL_STT_NOT_FOUND);
    }

    @Test
    void 한_화자의_STT만_있으면_준비되지_않은_것으로_처리한다() {
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L))
                .thenReturn(List.of(segment(1, 1, "안녕"), segment(2, 1, "들려?")));

        assertError(ErrorStatus.CALL_STT_NOT_READY);
    }

    @Test
    void 양쪽_STT가_있으면_저장_ID를_포함한_원본을_반환한다() {
        var segments = List.of(segment(1, 1, "안녕"), segment(2, 2, "반가워"));
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L)).thenReturn(segments);

        assertThat(service.findSegmentsForFeedback(1L, 10L)).containsExactlyElementsOf(segments);

        var order = inOrder(accessValidator, repository);
        order.verify(accessValidator).validateParticipant(10L, 1L);
        order.verify(repository).findByCallIdOrderByStartedAtMsAscIdAsc(10L);
    }

    @Test
    void 기존_대화_조회_결과의_화자와_원문을_유지한다() {
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L))
                .thenReturn(List.of(segment(1, 1, "안녕"), segment(2, 2, "반가워")));

        assertThat(service.findConversationForFeedback(1L, 10L)).containsExactly(
                new ConversationTurn("ME", "안녕"), new ConversationTurn("OTHER", "반가워"));
        verify(accessValidator).validateParticipant(10L, 1L);
    }

    @Test
    void 양쪽_화자_합계_최근_200개만_시간순으로_반환한다() {
        // 최근 200개가 한 화자의 발화여도 전체 녹음에 양쪽 STT가 있으면 준비 조건은 만족한다.
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id <= 50 ? 2 : 1, "발화 " + id)).toList();
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L)).thenReturn(segments);

        var selected = service.findSegmentsForFeedback(1L, 10L);

        assertThat(selected).hasSize(200).containsExactlyElementsOf(segments.subList(50, 250));
        assertThat(selected.getFirst().getId()).isEqualTo(51L);
        assertThat(selected.getLast().getId()).isEqualTo(250L);
    }

    @Test
    void 정확히_200개이면_전체를_유지한다() {
        var segments = IntStream.rangeClosed(1, 200)
                .mapToObj(id -> segment(id, id % 2 + 1, "발화 " + id)).toList();
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L)).thenReturn(segments);

        assertThat(service.findSegmentsForFeedback(1L, 10L)).containsExactlyElementsOf(segments);
    }

    @Test
    void 기존_피드백용_대화_조회에도_200개_제한이_적용된다() {
        var segments = IntStream.rangeClosed(1, 201)
                .mapToObj(id -> segment(id, id % 2 + 1, "발화 " + id)).toList();
        when(repository.findByCallIdOrderByStartedAtMsAscIdAsc(10L)).thenReturn(segments);

        var conversation = service.findConversationForFeedback(1L, 10L);

        assertThat(conversation).hasSize(200);
        assertThat(conversation.getFirst().message()).isEqualTo("발화 2");
        assertThat(conversation.getLast().message()).isEqualTo("발화 201");
    }
}
