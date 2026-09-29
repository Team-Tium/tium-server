package com.studiorent.tium.domain.call.entity;

import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "call_stt_segments",
        indexes = {
                @Index(name = "idx_call_stt_segments_call_time", columnList = "call_id, started_at_ms, id"),
                @Index(name = "idx_call_stt_segments_speaker", columnList = "speaker_user_id")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CallSttSegment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false)
    private Long callId;

    @Column(name = "speaker_user_id", nullable = false)
    private Long speakerUserId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "started_at_ms", nullable = false)
    private Long startedAtMs;

    @Column(name = "ended_at_ms", nullable = false)
    private Long endedAtMs;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    public static CallSttSegment create(
            Long callId,
            Long speakerUserId,
            Integer sequence,
            Long startedAtMs,
            Long endedAtMs,
            String text
    ) {
        return CallSttSegment.builder()
                .callId(callId)
                .speakerUserId(speakerUserId)
                .sequence(sequence)
                .startedAtMs(startedAtMs)
                .endedAtMs(endedAtMs)
                .text(text)
                .build();
    }
}
