package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.call.service.query.CallSttQueryService;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.feedback.converter.ConversationAnalysisInputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 권한과 조회 조건을 적용한 분석 입력 조회. 외부 LLM 호출은 수행하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationAnalysisInputQueryService {

    private static final int FEEDBACK_MESSAGE_LIMIT = 100;

    private final ChatRoomValidator chatRoomValidator;
    private final ChatMessageRepository chatMessageRepository;
    private final CallSttQueryService callSttQueryService;
    private final ConversationAnalysisInputConverter converter;

    public ConversationAnalysisInput findChatInput(Long memberId, Long roomId, RelationShip relationship) {
        chatRoomValidator.getJoinedMember(roomId, memberId);

        // 기존과 동일하게 전체 메시지 중 최근 100개를 조회한 뒤 분석 가능한 텍스트를 고른다.
        var messages = chatMessageRepository.findByChatRoomIdOrderByIdDesc(
                roomId, PageRequest.of(0, FEEDBACK_MESSAGE_LIMIT));
        return converter.fromChatMessages(memberId, relationship, messages);
    }

    public ConversationAnalysisInput findCallInput(Long memberId, Long callId, RelationShip relationship) {
        // 통화 참여 여부와 양쪽 녹음의 STT 준비 여부는 기존 통화 조회 서비스에서 확인한다.
        var segments = callSttQueryService.findSegmentsForFeedback(memberId, callId);
        return converter.fromCallSegments(memberId, relationship, segments);
    }



}
