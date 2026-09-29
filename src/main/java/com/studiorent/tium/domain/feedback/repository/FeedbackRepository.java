package com.studiorent.tium.domain.feedback.repository;

import com.studiorent.tium.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findTopByMemberIdAndRoomIdOrderByCreatedAtDesc(Long memberId, Long roomId);

    Optional<Feedback> findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(Long memberId, Long callId);

    boolean existsByMemberIdAndRoomIdAndCompletedTrue(Long memberId, Long roomId);

    boolean existsByMemberIdAndCallIdAndCompletedTrue(Long memberId, Long callId);

    boolean existsByMemberIdAndCallIdAndCompletedFalse(Long memberId, Long callId);

    boolean existsByCallIdAndCompletedTrue(Long callId);

    void deleteByMemberIdAndCallIdAndCompletedFalse(Long memberId, Long callId);

    void deleteByMemberIdAndCallIdAndCompletedFalseAndCreatedAtBefore(Long memberId, Long callId, LocalDateTime createdAt);
}
