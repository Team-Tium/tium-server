package com.studiorent.tium.domain.matching.service.command;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.matching.dto.MatchingRequestDTO;
import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import com.studiorent.tium.domain.matching.service.MatchingQueueService;
import com.studiorent.tium.domain.matching.socket.event.MatchingCompletedEvent;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchingCommandServiceImpl implements MatchingCommandService {

    private final MatchingQueueService matchingQueueService;
    private final ChatCommandService chatCommandService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 기다리는 상대가 있으면 그 자리에서 짝을 짓고 채팅방을 만든다. 없으면 대기열에 들어간다.
     *
     * <p>상대를 꺼내는 것과 방을 만드는 것이 한 트랜잭션이다. 방 생성이 실패하면 상대도 대기열로 돌아간다.
     * 두 사람에게 알리는 것은 커밋 뒤에 리스너가 한다.
     */
    @Override
    @Transactional
    public void enqueue(Long memberId, MatchingRequestDTO.EnqueueDTO request) {
        MatchingType type = request.type();
        if (type != MatchingType.CHAT) {
            throw new BusinessException(ErrorStatus.MATCHING_UNSUPPORTED_TYPE);
        }
        if (matchingQueueService.isWaiting(memberId)) {
            throw new BusinessException(ErrorStatus.MATCHING_ALREADY_WAITING);
        }

        matchingQueueService.pollOldest(type)
                .ifPresentOrElse(
                        opponentId -> match(memberId, opponentId),
                        () -> matchingQueueService.add(memberId, type)
                );
    }

    private void match(Long memberId, Long opponentId) {
        ChatResponseDTO.CreateRoomResultDTO room = chatCommandService.createRoom(
                memberId, new ChatRequestDTO.CreateRoomDTO(opponentId));

        applicationEventPublisher.publishEvent(new MatchingCompletedEvent(room.roomId(), memberId, opponentId));
    }

    @Override
    @Transactional
    public void cancel(Long memberId) {
        if (!matchingQueueService.remove(memberId)) {
            throw new BusinessException(ErrorStatus.MATCHING_NOT_WAITING);
        }
    }
}
