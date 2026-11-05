package com.studiorent.tium.domain.matching.service.command;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.matching.converter.MatchingConverter;
import com.studiorent.tium.domain.matching.dto.MatchingRequestDTO;
import com.studiorent.tium.domain.matching.dto.MatchingResponseDTO;
import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import com.studiorent.tium.domain.matching.service.MatchingQueueService;
import com.studiorent.tium.domain.matching.socket.event.MatchingCompletedEvent;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MatchingCommandServiceImpl implements MatchingCommandService {

    private final MatchingQueueService matchingQueueService;
    private final ChatCommandService chatCommandService;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 기다리는 상대가 있으면 그 자리에서 짝을 짓고 채팅방을 만든다. 없으면 대기열에 들어간다.
     *
     * <p>상대를 꺼내는 것과 방을 만드는 것이 한 트랜잭션이다. 방 생성이 실패하면 상대도 대기열로 돌아간다.
     * 두 사람에게 알리는 것은 커밋 뒤에 리스너가 한다. 요청한 사람은 응답으로도 방 ID를 받는다.
     */
    @Override
    @Transactional
    public MatchingResponseDTO.EnqueueResultDTO enqueue(Long memberId, MatchingRequestDTO.EnqueueDTO request) {
        MatchingType type = resolveType(request.type());
        if (matchingQueueService.isWaiting(memberId)) {
            throw new BusinessException(ErrorStatus.MATCHING_ALREADY_WAITING);
        }

        Optional<Long> opponentId = pollAvailableOpponent(type);
        if (opponentId.isEmpty()) {
            matchingQueueService.add(memberId, type);
            return MatchingConverter.toWaiting();
        }
        return match(memberId, opponentId.get());
    }

    private MatchingType resolveType(String type) {
        if (!MatchingType.CHAT.name().equals(type)) {
            throw new BusinessException(ErrorStatus.MATCHING_UNSUPPORTED_TYPE);
        }
        return MatchingType.CHAT;
    }

    /**
     * 탈퇴한 회원의 자리는 버리고 다음 사람을 꺼낸다.
     *
     * <p>탈퇴 회원과 방을 만들면 {@code MEMBER_WITHDRAWN}으로 롤백되어 그 자리가 대기열에 다시 남는다.
     * 그러면 뒤의 요청이 전부 같은 자리에서 실패하므로, 방을 만들기 전에 걸러서 자리를 지운 채로 커밋한다.
     */
    private Optional<Long> pollAvailableOpponent(MatchingType type) {
        while (true) {
            Optional<Long> polled = matchingQueueService.pollOldest(type);
            if (polled.isEmpty() || isActive(polled.get())) {
                return polled;
            }
        }
    }

    private boolean isActive(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(member -> !member.isWithdrawn())
                .isPresent();
    }

    private MatchingResponseDTO.EnqueueResultDTO match(Long memberId, Long opponentId) {
        ChatResponseDTO.CreateRoomResultDTO room = chatCommandService.createRoom(
                memberId, new ChatRequestDTO.CreateRoomDTO(opponentId));

        applicationEventPublisher.publishEvent(new MatchingCompletedEvent(room.roomId(), memberId, opponentId));
        return MatchingConverter.toMatched(room);
    }

    @Override
    @Transactional
    public void cancel(Long memberId) {
        if (!matchingQueueService.remove(memberId)) {
            throw new BusinessException(ErrorStatus.MATCHING_NOT_WAITING);
        }
    }
}
