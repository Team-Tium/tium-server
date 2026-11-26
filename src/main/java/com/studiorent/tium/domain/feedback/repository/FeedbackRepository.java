package com.studiorent.tium.domain.feedback.repository;

import com.studiorent.tium.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findTopByMemberIdAndRoomIdOrderByCreatedAtDesc(Long memberId, Long roomId);

    Optional<Feedback> findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(Long memberId, Long callId);

    boolean existsByMemberIdAndRoomIdAndCompletedTrue(Long memberId, Long roomId);

    boolean existsByMemberIdAndCallIdAndCompletedTrue(Long memberId, Long callId);

    boolean existsByMemberIdAndCallIdAndCompletedFalse(Long memberId, Long callId);

    boolean existsByCallIdAndCompletedTrue(Long callId);

    Optional<Feedback> findByIdAndMemberIdAndCallIdAndCompletedFalse(Long id, Long memberId, Long callId);

    @Modifying
    @Query("""
            delete from Feedback feedback
            where feedback.id = :claimId
              and feedback.memberId = :memberId
              and feedback.callId = :callId
              and feedback.completed = false
            """)
    int deleteByIdAndMemberIdAndCallIdAndCompletedFalse(
            @Param("claimId") Long claimId, @Param("memberId") Long memberId, @Param("callId") Long callId);

    void deleteByMemberIdAndCallIdAndCompletedFalseAndCreatedAtBefore(Long memberId, Long callId, LocalDateTime createdAt);
}
