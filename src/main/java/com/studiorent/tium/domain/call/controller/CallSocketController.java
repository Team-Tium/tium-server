package com.studiorent.tium.domain.call.controller;

import com.studiorent.tium.domain.call.dto.CallSocketDTO;
import com.studiorent.tium.domain.call.service.command.CallSignalingService;
import com.studiorent.tium.global.security.CustomUserDetails;
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

    /** Handles the receiver accepting an incoming call. */
    @MessageMapping("/call/{callId}/accept")
    public void accept(Principal principal, @DestinationVariable Long callId) {
        callSignalingService.accept(extractMemberId(principal), callId);
    }

    /** Handles the receiver rejecting an incoming call. */
    @MessageMapping("/call/{callId}/reject")
    public void reject(Principal principal, @DestinationVariable Long callId) {
        callSignalingService.reject(extractMemberId(principal), callId);
    }

    /** Receives and relays a unified WebRTC signal for an accepted call. */
    @MessageMapping("/call/{callId}/signal")
    public void signal(
            Principal principal,
            @DestinationVariable Long callId,
            @Payload CallSocketDTO.SignalRequestDTO request) {
        callSignalingService.relaySignal(extractMemberId(principal), callId, request);
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
