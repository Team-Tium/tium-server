package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.call.service.query.CallSttQueryService;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.feedback.converter.ConversationAnalysisInputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConversationAnalysisInputQueryServiceTest {

    private final ChatRoomValidator chatRoomValidator = mock(ChatRoomValidator.class);
    private final ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
    private final CallSttQueryService callSttQueryService = mock(CallSttQueryService.class);
    private final ConversationAnalysisInputConverter converter = mock(ConversationAnalysisInputConverter.class);
    private final ConversationAnalysisInputQueryService service = new ConversationAnalysisInputQueryService(
            chatRoomValidator, chatMessageRepository, callSttQueryService, converter);

    @Test
    void 채팅_참여_확인_후_최근_100개를_조회하고_관계와_함께_변환한다() {
        var messages = List.of(ChatMessage.builder().id(20L).senderId(1L).content("안녕").build());
        var expected = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME,
                List.of(new Utterance("chat:20", Speaker.ME, "안녕")));
        when(chatMessageRepository.findByChatRoomIdOrderByIdDesc(10L, PageRequest.of(0, 100))).thenReturn(messages);
        when(converter.fromChatMessages(1L, RelationShip.STRANGER, messages)).thenReturn(expected);

        assertThat(service.findChatInput(1L, 10L, RelationShip.STRANGER)).isSameAs(expected);

        var order = inOrder(chatRoomValidator, chatMessageRepository, converter);
        order.verify(chatRoomValidator).getJoinedMember(10L, 1L);
        order.verify(chatMessageRepository).findByChatRoomIdOrderByIdDesc(10L, PageRequest.of(0, 100));
        order.verify(converter).fromChatMessages(1L, RelationShip.STRANGER, messages);
    }

    @Test
    void 채팅_권한이_없으면_내용을_조회하거나_변환하지_않는다() {
        var forbidden = new BusinessException(ErrorStatus.CHAT_NOT_ROOM_MEMBER);
        when(chatRoomValidator.getJoinedMember(10L, 1L)).thenThrow(forbidden);

        assertThatThrownBy(() -> service.findChatInput(1L, 10L, RelationShip.STRANGER)).isSameAs(forbidden);
        verifyNoInteractions(chatMessageRepository, converter);
    }

    @Test
    void 통화_준비_검증을_거친_원본_세그먼트와_관계를_변환기에_전달한다() {
        var segments = List.of(CallSttSegment.builder().id(20L).text("안녕").build());
        var expected = new ConversationAnalysisInput(RelationShip.FRIEND, Speaker.ME,
                List.of(new Utterance("stt:20", Speaker.ME, "안녕")));
        when(callSttQueryService.findSegmentsForFeedback(1L, 10L)).thenReturn(segments);
        when(converter.fromCallSegments(1L, RelationShip.FRIEND, segments)).thenReturn(expected);

        assertThat(service.findCallInput(1L, 10L, RelationShip.FRIEND)).isSameAs(expected);

        var order = inOrder(callSttQueryService, converter);
        order.verify(callSttQueryService).findSegmentsForFeedback(1L, 10L);
        order.verify(converter).fromCallSegments(1L, RelationShip.FRIEND, segments);
    }

    @Test
    void 통화_STT가_준비되지_않으면_변환하지_않는다() {
        var notReady = new BusinessException(ErrorStatus.CALL_STT_NOT_READY);
        when(callSttQueryService.findSegmentsForFeedback(1L, 10L)).thenThrow(notReady);

        assertThatThrownBy(() -> service.findCallInput(1L, 10L, RelationShip.FRIEND)).isSameAs(notReady);
        verifyNoInteractions(converter);
    }
}
