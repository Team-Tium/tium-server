package com.studiorent.tium.domain.call.entity;

import com.studiorent.tium.domain.call.entity.enums.CallSttJobStatus;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "call_stt_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_call_stt_job_call_speaker",
                columnNames = {"call_id", "speaker_user_id"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CallSttJob extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false)
    private Long callId;

    @Column(name = "speaker_user_id", nullable = false)
    private Long speakerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallSttJobStatus status;

    @Column(name = "attempt_id", nullable = false, length = 36)
    private String attemptId;

    @Column(name = "failed_reason", length = 50)
    private String failedReason;

    public static CallSttJob analyzing(Long callId, Long speakerUserId, String attemptId) {
        return CallSttJob.builder()
                .callId(callId)
                .speakerUserId(speakerUserId)
                .status(CallSttJobStatus.ANALYZING)
                .attemptId(attemptId)
                .failedReason(null)
                .build();
    }

    public void restart(String attemptId) {
        this.status = CallSttJobStatus.ANALYZING;
        this.attemptId = attemptId;
        this.failedReason = null;
    }

    public void complete() {
        this.status = CallSttJobStatus.DONE;
        this.failedReason = null;
    }

    public void fail(String failedReason) {
        this.status = CallSttJobStatus.FAILED;
        this.failedReason = failedReason;
    }

    public boolean isCurrentAttempt(String attemptId) {
        return this.attemptId.equals(attemptId);
    }
}
