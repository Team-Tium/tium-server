package com.studiorent.tium.domain.feedback.repository;

import com.studiorent.tium.domain.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
}
