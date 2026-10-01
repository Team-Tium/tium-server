package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.CallSttJob;
import com.studiorent.tium.domain.call.entity.enums.CallSttJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CallSttJobRepository extends JpaRepository<CallSttJob, Long> {

    Optional<CallSttJob> findByCallIdAndSpeakerUserId(Long callId, Long speakerUserId);

    boolean existsByCallIdAndSpeakerUserIdAndStatus(Long callId, Long speakerUserId, CallSttJobStatus status);
}
