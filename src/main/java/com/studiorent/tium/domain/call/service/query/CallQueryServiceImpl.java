package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.converter.CallConverter;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CallQueryServiceImpl implements CallQueryService {

    private final CallRepository callRepository;
    private final MemberRepository memberRepository;
    private final CallSttQueryService callSttQueryService;

    @Override
    @Transactional(readOnly = true)
    public CallSttResponseDTO.RecentCallList findRecentFeedbackCalls(Long memberId, Long cursor, int size) {
        List<Call> rows = callRepository.findRecentCalls(
                memberId,
                CallStatus.COMPLETED,
                cursor,
                PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<Call> page = hasNext ? rows.subList(0, size) : rows;

        Map<Long, Member> membersById = findOpponentMembers(memberId, page);
        List<CallSttResponseDTO.RecentCall> items = page.stream()
                .map(call -> toRecentCall(memberId, call, membersById))
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new CallSttResponseDTO.RecentCallList(items, hasNext, nextCursor);
    }

    private Map<Long, Member> findOpponentMembers(Long memberId, List<Call> calls) {
        List<Long> opponentIds = calls.stream()
                .map(call -> getOpponentId(call, memberId))
                .distinct()
                .toList();

        return memberRepository.findAllById(opponentIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
    }

    private CallSttResponseDTO.RecentCall toRecentCall(
            Long memberId,
            Call call,
            Map<Long, Member> membersById
    ) {
        Long opponentId = getOpponentId(call, memberId);
        Member opponent = membersById.get(opponentId);
        if (opponent == null) {
            throw new BusinessException(ErrorStatus.AUTH_MEMBER_NOT_FOUND);
        }

        CallSttResponseDTO.Status feedbackStatus = callSttQueryService.getFeedbackStatus(memberId, call);

        return new CallSttResponseDTO.RecentCall(
                call.getId(),
                new CallSttResponseDTO.Opponent(
                        opponent.getId(),
                        opponent.getName(),
                        opponent.getProfileImageUrl()),
                CallConverter.toServiceOffsetDateTime(call.getStartAt()),
                CallConverter.toServiceOffsetDateTime(call.getEndAt()),
                calculateDurationSeconds(call),
                feedbackStatus.feedbackCreated(),
                feedbackStatus.status(),
                feedbackStatus.message());
    }

    private static Long calculateDurationSeconds(Call call) {
        if (call.getEndAt() == null) {
            return 0L;
        }
        return Duration.between(call.getStartAt(), call.getEndAt()).getSeconds();
    }

    private static Long getOpponentId(Call call, Long memberId) {
        return call.getParticipants().stream()
                .map(MemberCall::getMemberId)
                .filter(participantId -> !participantId.equals(memberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorStatus.BAD_REQUEST));
    }
}
