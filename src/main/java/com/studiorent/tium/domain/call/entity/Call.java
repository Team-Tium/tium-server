package com.studiorent.tium.domain.call.entity;

import com.studiorent.tium.domain.call.entity.enums.CallStatus;
import com.studiorent.tium.domain.call.entity.enums.CallType;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "call_sessions")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Call extends BaseEntity {

    @Id
    @Column(name = "call_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallStatus status;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "caller_member_id", nullable = false)
    private Long callerMemberId;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Builder.Default
    @OneToMany(mappedBy = "call", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MemberCall> participants = new LinkedHashSet<>();

    /** Creates a new 1:1 call in the ringing IN_PROGRESS state. */
    public static Call start(CallType type, Long callerMemberId) {
        return Call.builder()
                .type(type)
                .status(CallStatus.IN_PROGRESS)
                .startAt(LocalDateTime.now())
                .endAt(null)
                .callerMemberId(callerMemberId)
                .acceptedAt(null)
                .build();
    }

    public void addParticipant(Long memberId) {
        if (hasParticipant(memberId)) {
            return;
        }

        participants.add(MemberCall.create(this, memberId));
    }

    public boolean hasParticipant(Long memberId) {
        return participants.stream()
                .anyMatch(participant -> participant.isMember(memberId));
    }

    /** Records receiver acceptance while keeping the call IN_PROGRESS. */
    public void accept(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    /** Ends an accepted call with the normal completed terminal status. */
    public void complete(LocalDateTime endAt) {
        end(CallStatus.COMPLETED, endAt);
    }

    /** Ends an unaccepted caller-canceled or unanswered call. */
    public void cancel(LocalDateTime endAt) {
        end(CallStatus.CANCELED, endAt);
    }

    /** Ends an unaccepted call explicitly rejected by the receiver. */
    public void reject(LocalDateTime endAt) {
        end(CallStatus.REJECTED, endAt);
    }

    /** Ends an active call after the socket disconnect grace period expires. */
    public void disconnect(LocalDateTime endAt) {
        end(CallStatus.DISCONNECTED, endAt);
    }

    public boolean isInProgress() {
        return this.status == CallStatus.IN_PROGRESS;
    }

    /** Returns whether the call has reached any non-IN_PROGRESS outcome. */
    public boolean isTerminal() {
        return this.status != CallStatus.IN_PROGRESS;
    }

    /** Returns whether the receiver has accepted this call. */
    public boolean isAccepted() {
        return this.acceptedAt != null;
    }

    /** Returns whether the supplied member created this call. */
    public boolean isCaller(Long memberId) {
        return Objects.equals(this.callerMemberId, memberId);
    }

    /** Resolves the one non-caller participant for role enforcement. */
    public Long getReceiverMemberId() {
        if (callerMemberId == null) {
            return null;
        }

        return participants.stream()
                .map(MemberCall::getMemberId)
                .filter(memberId -> !memberId.equals(callerMemberId))
                .findFirst()
                .orElse(null);
    }

    private void end(CallStatus status, LocalDateTime endAt) {
        this.status = status;
        this.endAt = endAt;
    }
}
