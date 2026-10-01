package com.studiorent.tium.domain.call.service;

import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.exception.CallErrorStatus;
import com.studiorent.tium.domain.call.repository.CallRepository;
import com.studiorent.tium.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CallAccessValidator {

    private final CallRepository callRepository;

    /** Loads a call and validates that the authenticated member participates in it. */
    @Transactional(readOnly = true)
    public Call validateParticipant(Long callId, Long memberId) {
        Call call = callRepository.findWithParticipantsById(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));

        validateParticipant(call, memberId);

        return call;
    }

    /** Loads a call with a write lock and validates that the authenticated member participates in it. */
    @Transactional
    public Call validateParticipantForUpdate(Long callId, Long memberId) {
        Call call = callRepository.findWithParticipantsByIdForUpdate(callId)
                .orElseThrow(() -> new BusinessException(CallErrorStatus.CALL_NOT_FOUND));

        validateParticipant(call, memberId);

        return call;
    }

    private static void validateParticipant(Call call, Long memberId) {
        if (!call.hasParticipant(memberId)) {
            throw new BusinessException(CallErrorStatus.CALL_FORBIDDEN);
        }
    }

    /** Loads a call and validates that the participant can signal while the call is active. */
    @Transactional(readOnly = true)
    public Call validateActiveParticipant(Long callId, Long memberId) {
        Call call = validateParticipant(callId, memberId);

        if (call.isTerminal()) {
            throw new BusinessException(CallErrorStatus.CALL_ALREADY_COMPLETED);
        }

        return call;
    }
}
