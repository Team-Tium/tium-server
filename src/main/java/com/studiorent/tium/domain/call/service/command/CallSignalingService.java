package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;

public interface CallSignalingService {

    void accept(Long memberId, Long callId);

    void reject(Long memberId, Long callId);

    void relayOffer(Long memberId, Long callId, CallSocketDTO.SdpDTO request);

    void relayAnswer(Long memberId, Long callId, CallSocketDTO.SdpDTO request);

    void relayIceCandidate(Long memberId, Long callId, CallSocketDTO.IceCandidateDTO request);

    void end(Long memberId, Long callId);
}
