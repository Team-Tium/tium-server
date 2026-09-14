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

    @Builder.Default
    @OneToMany(mappedBy = "call", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MemberCall> participants = new LinkedHashSet<>();

    public static Call start(CallType type) {
        return Call.builder()
                .type(type)
                .status(CallStatus.IN_PROGRESS)
                .startAt(LocalDateTime.now())
                .endAt(null)
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

    public void complete() {
        this.status = CallStatus.COMPLETED;
        this.endAt = LocalDateTime.now();
    }

    public boolean isInProgress() {
        return this.status == CallStatus.IN_PROGRESS;
    }

    public boolean isCompleted() {
        return this.status == CallStatus.COMPLETED;
    }
}
