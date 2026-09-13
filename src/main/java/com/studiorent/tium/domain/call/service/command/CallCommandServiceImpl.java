package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.converter.CallConverter;
import com.studiorent.tium.domain.call.dto.CallRequestDTO;
import com.studiorent.tium.domain.call.dto.CallResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CallCommandServiceImpl implements CallCommandService {

    private final CallRepository callRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public CallResponseDTO.CallResultDTO startCall(Long memberId, CallRequestDTO.CallStartDTO request) {
        validateMemberExists(memberId);

        Call call = Call.start(request.type());
        call.addParticipant(memberId);

        for (Long participantId : resolveParticipantIds(request.participantIds())) {
            validateMemberExists(participantId);
            call.addParticipant(participantId);
        }

        return CallConverter.toCallResult(callRepository.save(call));
    }

    @Override
    @Transactional
    public CallResponseDTO.CallResultDTO endCall(Long memberId, Long callId) {
        Call call = findCall(callId);
        validateParticipant(call, memberId);

        if (call.isCompleted()) {
            throw new BusinessException(CallErrorStatus.CALL_ALREADY_COMPLETED);
        }

        call.complete();

        return CallConverter.toCallResult(call);
    }

    private Call findCall(Long callId) {
        return callRepository.findWithParticipantsById(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));
    }

    private void validateMemberExists(Long memberId) {
        if (!memberRepository.existsById(memberId)) {
            throw new BusinessException(ErrorStatus.AUTH_MEMBER_NOT_FOUND);
        }
    }

    private void validateParticipant(Call call, Long memberId) {
        if (!call.hasParticipant(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
    }

    private Set<Long> resolveParticipantIds(Set<Long> participantIds) {
        if (participantIds == null) {
            return Set.of();
        }

        return new LinkedHashSet<>(participantIds);
    }
}
