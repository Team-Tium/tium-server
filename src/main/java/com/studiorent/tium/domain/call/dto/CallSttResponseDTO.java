package com.studiorent.tium.domain.call.dto;

import com.studiorent.tium.domain.call.entity.enums.CallFeedbackStatus;

import java.time.OffsetDateTime;
import java.util.List;

public class CallSttResponseDTO {

    public record SaveResult(
            Long callId,
            Long speakerUserId,
            CallFeedbackStatus status,
            String message
    ) {
    }

    public record SegmentList(
            Long callId,
            List<Segment> segments
    ) {
    }

    public record Status(
            Long callId,
            CallFeedbackStatus status,
            Boolean mySttSaved,
            Boolean otherSttSaved,
            Boolean feedbackReady,
            Boolean feedbackCreated,
            String missingRecordingOwner,
            String failedReason,
            String message
    ) {
    }

    public record RecentCallList(
            List<RecentCall> items,
            Boolean hasNext,
            Long nextCursor
    ) {
    }

    public record RecentCall(
            Long callId,
            Opponent opponent,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            Long durationSeconds,
            Boolean hasFeedback,
            CallFeedbackStatus status,
            String message
    ) {
    }

    public record Opponent(
            Long userId,
            String nickname,
            String profileImageUrl
    ) {
    }

    public record Segment(
            String speaker,
            Long startMs,
            Long endMs,
            String text
    ) {
    }
}
