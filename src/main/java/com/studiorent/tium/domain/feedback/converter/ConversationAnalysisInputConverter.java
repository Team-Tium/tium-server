package com.studiorent.tium.domain.feedback.converter;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisValidator;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 참여 권한 확인 후 조회한 채팅/통화 데이터를 분석 입력으로 변환한다.
 * 조회 범위, 최근 메시지 수 제한, 통화 양쪽 STT 준비 여부는 호출 서비스에서 확인한다.
 */
@Component
@RequiredArgsConstructor
public class ConversationAnalysisInputConverter {

    private final ConversationAnalysisValidator validator;

    public ConversationAnalysisInput fromChatMessages(
            Long memberId, RelationShip relationship, List<ChatMessage> messages
    ) {
        List<Utterance> utterances = messages.stream()
                .filter(message -> message.getMessageType() == MessageType.TEXT && !message.isDeleted())
                // 채팅의 시간순 기준은 전역 순증가하는 저장 메시지 ID다.
                .sorted(Comparator.comparing(message -> requireId(message.getId())))
                .map(message -> new Utterance(
                        "chat:" + requireId(message.getId()),
                        toSpeaker(memberId, message.getSenderId()),
                        message.getContent()))
                .toList();

        return toInput(relationship, utterances);
    }

    public ConversationAnalysisInput fromCallSegments(
            Long memberId, RelationShip relationship, List<CallSttSegment> segments
    ) {
        List<Utterance> utterances = segments.stream()
                // 화자별 sequence가 아닌 통화 전체 시작 시각으로 정렬한다.
                .sorted(Comparator.comparing((CallSttSegment segment) -> requireStartTime(segment.getStartedAtMs()))
                        .thenComparing(segment -> requireId(segment.getId())))
                .map(segment -> {
                    requireStartTime(segment.getStartedAtMs());
                    return new Utterance(
                            "stt:" + requireId(segment.getId()),
                            toSpeaker(memberId, segment.getSpeakerUserId()),
                            segment.getText());
                })
                .toList();

        return toInput(relationship, utterances);
    }

    private ConversationAnalysisInput toInput(RelationShip relationship, List<Utterance> utterances) {
        if (utterances.isEmpty()) {
            throw new BusinessException(ErrorStatus.FEEDBACK_CONVERSATION_EMPTY);
        }
        var input = new ConversationAnalysisInput(relationship, Speaker.ME, utterances);
        validator.validateInput(input);
        return input;
    }

    private Speaker toSpeaker(Long memberId, Long speakerId) {
        if (memberId == null) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT);
        }
        if (speakerId == null) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_SPEAKER);
        }
        return memberId.equals(speakerId) ? Speaker.ME : Speaker.OTHER;
    }

    private Long requireId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_ID);
        }
        return id;
    }

    private Long requireStartTime(Long startedAtMs) {
        if (startedAtMs == null || startedAtMs < 0) {
            throw new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_SOURCE_TIME);
        }
        return startedAtMs;
    }
}
