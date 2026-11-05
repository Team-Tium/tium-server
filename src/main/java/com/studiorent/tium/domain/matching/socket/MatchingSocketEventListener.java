package com.studiorent.tium.domain.matching.socket;

import com.studiorent.tium.domain.chat.converter.ChatConverter;
import com.studiorent.tium.domain.matching.dto.MatchingSocketDTO;
import com.studiorent.tium.domain.matching.socket.event.MatchingCompletedEvent;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import com.studiorent.tium.global.socket.SocketEvent;
import com.studiorent.tium.global.socket.SocketEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.studiorent.tium.domain.matching.socket.MatchingSocketEventType.MATCHED;

@Component
@RequiredArgsConstructor
public class MatchingSocketEventListener {

    private final SocketEventPublisher socketEventPublisher;
    private final MemberRepository memberRepository;

    /** 두 사람의 개인 주소로 방 ID와 상대 정보를 보낸다. 방이 커밋된 뒤에 보내야 받은 쪽이 바로 들어갈 수 있다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMatched(MatchingCompletedEvent event) {
        Member member = findMember(event.memberId());
        Member opponent = findMember(event.opponentId());

        socketEventPublisher.toMember(member.getId(), matched(event.roomId(), opponent));
        socketEventPublisher.toMember(opponent.getId(), matched(event.roomId(), member));
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.INTERNAL_SERVER_ERROR));
    }

    private SocketEvent<MatchingSocketDTO.MatchedDTO> matched(Long roomId, Member opponent) {
        return new SocketEvent<>(MATCHED, new MatchingSocketDTO.MatchedDTO(roomId, ChatConverter.toOpponent(opponent)));
    }
}
