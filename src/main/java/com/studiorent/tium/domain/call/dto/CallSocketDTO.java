package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallType;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public class CallSocketDTO {

    public record IncomingCallDTO(
            Long callId,
            CallType type,
            Long callerMemberId
    ) {
    }

    public record CallAcceptedDTO(
            Long callId,
            CallType type,
            Long senderMemberId,
            OffsetDateTime acceptedAt
    ) {
    }

    public record CallRejectedDTO(
            Long callId,
            CallType type,
            Long senderMemberId
    ) {
    }

    public record SdpDTO(
            @NotBlank(message = "sdp is required")
            String sdp
    ) {
    }

    public record SdpSignalDTO(
            Long callId,
            CallType type,
            Long senderMemberId,
            String sdp
    ) {
    }

    public record IceCandidateDTO(
            @NotBlank(message = "candidate is required")
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex
    ) {
    }

    public record IceCandidateSignalDTO(
            Long callId,
            CallType type,
            Long senderMemberId,
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex
    ) {
    }

    public record CallEndedDTO(
            Long callId,
            CallType type,
            Long endedByMemberId,
            OffsetDateTime endAt
    ) {
    }
}
