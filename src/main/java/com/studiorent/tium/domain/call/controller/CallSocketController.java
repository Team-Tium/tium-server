package com.studiorent.tium.domain.call.controller;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.service.command.CallSignalingService;
import com.studiorent.tium.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CallSocketController {

    private final CallSignalingService callSignalingService;

    /** Handles a participant accepting an incoming call. */
    @MessageMapping("/call/{callId}/accept")
    public void accept(Principal principal, @DestinationVariable Long callId) {
        callSignalingService.accept(extractMemberId(principal), callId);
    }

    /** Handles a participant rejecting an incoming call. */
    @MessageMapping("/call/{callId}/reject")
    public void reject(Principal principal, @DestinationVariable Long callId) {
        callSignalingService.reject(extractMemberId(principal), callId);
    }

    /** Receives and relays a WebRTC SDP offer for a call. */
    @MessageMapping("/call/{callId}/offer")
    public void offer(
            Principal principal,
            @DestinationVariable Long callId,
            @Valid @Payload CallSocketDTO.SdpDTO request) {
        callSignalingService.relayOffer(extractMemberId(principal), callId, request);
    }

    /** Receives and relays a WebRTC SDP answer for a call. */
    @MessageMapping("/call/{callId}/answer")
    public void answer(
            Principal principal,
            @DestinationVariable Long callId,
            @Valid @Payload CallSocketDTO.SdpDTO request) {
        callSignalingService.relayAnswer(extractMemberId(principal), callId, request);
    }

    /** Receives and relays a WebRTC ICE candidate for a call. */
    @MessageMapping("/call/{callId}/ice")
    public void ice(
            Principal principal,
            @DestinationVariable Long callId,
            @Valid @Payload CallSocketDTO.IceCandidateDTO request) {
        callSignalingService.relayIceCandidate(extractMemberId(principal), callId, request);
    }

    /** Ends a call through the existing persistent lifecycle logic. */
    @MessageMapping("/call/{callId}/end")
    public void end(Principal principal, @DestinationVariable Long callId) {
        callSignalingService.end(extractMemberId(principal), callId);
    }

    /** Extracts the authenticated member ID attached by the shared STOMP authentication. */
    private Long extractMemberId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }

        throw new IllegalStateException("Authenticated socket principal is required");
    }
}
