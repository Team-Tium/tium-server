package com.studiorent.tium.domain.feedback.repository;

import com.studiorent.tium.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findTopByMemberIdAndRoomIdOrderByCreatedAtDesc(Long memberId, Long roomId);
}
