package com.studiorent.tium.domain.call.repository;

import com.studiorent.tium.domain.call.entity.CallSttSegment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CallSttSegmentRepository extends JpaRepository<CallSttSegment, Long> {

    List<CallSttSegment> findByCallIdOrderByStartedAtMsAscIdAsc(Long callId);

    List<CallSttSegment> findByCallIdOrderByStartedAtMsDescIdDesc(Long callId, Pageable pageable);

    boolean existsByCallIdAndSpeakerUserId(Long callId, Long speakerUserId);

    void deleteByCallIdAndSpeakerUserId(Long callId, Long speakerUserId);
}
