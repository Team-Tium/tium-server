package com.studiorent.tium.domain.call;

import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.CallSttJob;
import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.call.entity.enums.CallFeedbackStatus;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallSttJobRepository;
import com.studiorent.tium.domain.call.repository.CallSttSegmentRepository;
import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.call.service.query.CallSttQueryServiceImpl;
import com.studiorent.tium.domain.feedback.converter.ConversationAnalysisInputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisValidator;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CallSttFeedbackConversationTest {

    private final CallAccessValidator accessValidator = mock(CallAccessValidator.class);
    private final CallSttSegmentRepository repository = mock(CallSttSegmentRepository.class);
    private final CallSttJobRepository jobRepository = mock(CallSttJobRepository.class);
    private final FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
    private final CallSttQueryServiceImpl service = new CallSttQueryServiceImpl(
            accessValidator, jobRepository, repository, feedbackRepository);

    @BeforeEach
    void setUp() {
        when(accessValidator.validateParticipant(10L, 1L)).thenReturn(call());
        when(accessValidator.validateParticipant(10L, 2L)).thenReturn(call());
    }

    private Call call() {
        var call = Call.builder().id(10L).build();
        call.addParticipant(1L);
        call.addParticipant(2L);
        return call;
    }

    private CallSttSegment segment(long id, long speakerId, String text) {
        return CallSttSegment.builder().id(id).callId(10L).speakerUserId(speakerId)
                .sequence(1).startedAtMs(id * 100).endedAtMs(id * 100 + 50).text(text).build();
    }

    private void givenSegments(List<CallSttSegment> segments) {
        var latest = segments.subList(Math.max(0, segments.size() - 200), segments.size());
        when(repository.findByCallIdOrderByStartedAtMsDescIdDesc(10L, PageRequest.of(0, 200)))
                .thenReturn(latest.reversed());
        when(repository.existsByCallIdAndSpeakerUserId(10L, 1L))
                .thenReturn(segments.stream().anyMatch(segment -> segment.getSpeakerUserId().equals(1L)));
        when(repository.existsByCallIdAndSpeakerUserId(10L, 2L))
                .thenReturn(segments.stream().anyMatch(segment -> segment.getSpeakerUserId().equals(2L)));
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
        givenSegments(List.of());

        assertError(ErrorStatus.CALL_STT_NOT_FOUND);
    }

    @Test
    void 한_화자의_STT만_있으면_준비되지_않은_것으로_처리한다() {
        givenSegments(List.of(segment(1, 1, "안녕"), segment(2, 1, "들려?")));

        assertError(ErrorStatus.CALL_STT_NOT_READY);
    }

    @Test
    void 양쪽_STT가_있으면_저장_ID를_포함한_원본을_반환한다() {
        var segments = List.of(segment(1, 1, "안녕"), segment(2, 2, "반가워"));
        givenSegments(segments);

        assertThat(service.findSegmentsForFeedback(1L, 10L)).containsExactlyElementsOf(segments);

        var order = inOrder(accessValidator, repository);
        order.verify(accessValidator).validateParticipant(10L, 1L);
        order.verify(repository).findByCallIdOrderByStartedAtMsDescIdDesc(10L, PageRequest.of(0, 200));
    }

    @Test
    void 양쪽_화자_합계_최근_200개만_시간순으로_반환한다() {
        // 상대 발화가 최근 범위 밖이어도 요청자 본인의 발화가 있으면 생성할 수 있다.
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id <= 50 ? 2 : 1, "발화 " + id)).toList();
        givenSegments(segments);

        var selected = service.findSegmentsForFeedback(1L, 10L);

        assertThat(selected).hasSize(200).containsExactlyElementsOf(segments.subList(50, 250));
        assertThat(selected.getFirst().getId()).isEqualTo(51L);
        assertThat(selected.getLast().getId()).isEqualTo(250L);
    }

    @Test
    void 정확히_200개이면_전체를_유지한다() {
        var segments = IntStream.rangeClosed(1, 200)
                .mapToObj(id -> segment(id, id % 2 + 1, "발화 " + id)).toList();
        givenSegments(segments);

        assertThat(service.findSegmentsForFeedback(1L, 10L)).containsExactlyElementsOf(segments);
    }

    @Test
    void 요청자_발화가_최근_200개_밖이면_준비_상태로_표시하지_않는다() {
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id <= 50 ? 2 : 1, "발화 " + id)).toList();
        givenSegments(segments);

        var status = service.getFeedbackStatus(2L, call());

        assertThat(status.mySttSaved()).isTrue();
        assertThat(status.otherSttSaved()).isTrue();
        assertThat(status.feedbackReady()).isFalse();
        assertThat(status.status()).isEqualTo(CallFeedbackStatus.TARGET_SPEAKER_MISSING);
        assertThat(status.missingRecordingOwner()).isNull();
        assertThat(status.failedReason()).isNull();
        assertThat(status.message()).isEqualTo("최근 200개 발화에 본인의 발화가 없어 피드백을 생성할 수 없습니다.");
    }

    @Test
    void 요청자_발화가_최근_200개_밖이면_입력_변환_전에_생성을_거절한다() {
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id <= 50 ? 2 : 1, "발화 " + id)).toList();
        givenSegments(segments);

        assertThatThrownBy(() -> service.findSegmentsForFeedback(2L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getBaseCode())
                                .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_TARGET_SPEAKER_MISSING));
    }

    @Test
    void 같은_통화라도_요청자_발화가_최근_200개에_있으면_준비와_입력_생성이_가능하다() {
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id <= 50 ? 2 : 1, "발화 " + id)).toList();
        givenSegments(segments);

        var status = service.getFeedbackStatus(1L, call());
        var selected = service.findSegmentsForFeedback(1L, 10L);

        assertThat(status.status()).isEqualTo(CallFeedbackStatus.READY);
        assertThat(status.feedbackReady()).isTrue();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var converter = new ConversationAnalysisInputConverter(
                    new ConversationAnalysisValidator(factory.getValidator()));
            var input = converter.fromCallSegments(1L, RelationShip.FRIEND, selected);
            assertThat(input.messages()).hasSize(200);
            assertThat(input.messages()).allMatch(message -> message.speaker() == Speaker.ME);
        }
    }

    @Test
    void 요청자_마지막_발화가_최근_200개의_시작_경계에_있으면_준비와_생성이_가능하다() {
        var segments = IntStream.rangeClosed(1, 250)
                .mapToObj(id -> segment(id, id == 51 ? 2 : 1, "발화 " + id)).toList();
        givenSegments(segments);

        var status = service.getFeedbackStatus(2L, call());
        var selected = service.findSegmentsForFeedback(2L, 10L);

        assertThat(status.status()).isEqualTo(CallFeedbackStatus.READY);
        assertThat(status.feedbackReady()).isTrue();
        assertThat(selected).hasSize(200).containsExactlyElementsOf(segments.subList(50, 250));
        assertThat(selected.getFirst().getSpeakerUserId()).isEqualTo(2L);
    }

    @ParameterizedTest
    @EnumSource(value = CallFeedbackStatus.class, names = {"DONE", "GENERATING", "ANALYZING", "FAILED"})
    void 기존_진행_상태를_대상_발화_없음으로_덮어쓰지_않는다(CallFeedbackStatus expected) {
        when(repository.existsByCallIdAndSpeakerUserId(10L, 1L)).thenReturn(true);
        when(repository.existsByCallIdAndSpeakerUserId(10L, 2L)).thenReturn(true);
        if (expected == CallFeedbackStatus.DONE) {
            when(feedbackRepository.existsByMemberIdAndCallIdAndCompletedTrue(2L, 10L)).thenReturn(true);
        } else if (expected == CallFeedbackStatus.GENERATING) {
            when(feedbackRepository.existsByMemberIdAndCallIdAndCompletedFalse(2L, 10L)).thenReturn(true);
        } else {
            var job = CallSttJob.analyzing(10L, 2L, "attempt");
            if (expected == CallFeedbackStatus.FAILED) {
                job.fail("failed");
            }
            when(jobRepository.findByCallIdAndSpeakerUserId(10L, 2L)).thenReturn(Optional.of(job));
        }

        var status = service.getFeedbackStatus(2L, call());

        assertThat(status.status()).isEqualTo(expected);
        assertThat(status.feedbackReady()).isFalse();
        verify(repository, never()).findByCallIdOrderByStartedAtMsDescIdDesc(anyLong(), any());
    }

}
