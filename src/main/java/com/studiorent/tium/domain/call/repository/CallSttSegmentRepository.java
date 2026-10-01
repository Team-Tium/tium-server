package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallSttSegmentRepository extends JpaRepository<CallSttSegment, Long> {

    List<CallSttSegment> findByCallIdOrderByStartedAtMsAscIdAsc(Long callId);

    boolean existsByCallIdAndSpeakerUserId(Long callId, Long speakerUserId);

    void deleteByCallIdAndSpeakerUserId(Long callId, Long speakerUserId);
}
