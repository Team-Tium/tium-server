package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;

public interface CallSignalingService {

    void accept(Long memberId, Long callId);

    void reject(Long memberId, Long callId);

    void relaySignal(Long memberId, Long callId, CallSocketDTO.SignalRequestDTO request);
}
