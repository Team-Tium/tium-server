package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallEndedReason;
import com.studiorent.tium.domain.call.entity.enums.CallSignalKind;
import com.studiorent.tium.domain.call.entity.enums.CallType;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class CallSocketDTO {

    public record IncomingCallDTO(
            Long callId,
            CallType type,
            CallerDTO caller,
            LocalDateTime createdAt
    ) {
    }

    public record CallerDTO(
            Long userId,
            String nickname
    ) {
    }

    public record AcceptedDTO(
            Long callId,
            OffsetDateTime acceptedAt
    ) {
    }

    public record SignalRequestDTO(
            CallSignalKind kind,
            String sdp,
            IceCandidateDTO candidate
    ) {
    }

    public record SignalDTO(
            Long callId,
            Long fromMemberId,
            CallSignalKind kind,
            String sdp,
            IceCandidateDTO candidate
    ) {
    }

    public record IceCandidateDTO(
            String candidate,
            String sdpMid,
            Integer sdpMLineIndex
    ) {
    }

    public record CallEndedDTO(
            Long callId,
            Long endedBy,
            CallEndedReason reason,
            OffsetDateTime endAt
    ) {
    }
}
