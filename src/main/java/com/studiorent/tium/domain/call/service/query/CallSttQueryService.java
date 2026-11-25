package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.CallSttSegment;

import java.util.List;

public interface CallSttQueryService {

    int FEEDBACK_UTTERANCE_LIMIT = 200;

    CallSttResponseDTO.SegmentList findStt(Long memberId, Long callId);

    CallSttResponseDTO.Status findSttStatus(Long memberId, Long callId);

    CallSttResponseDTO.Status getFeedbackStatus(Long memberId, Call call);

    // 권한, 양쪽 STT 저장 및 최근 200개 안의 요청자 발화를 확인한 뒤 시간순 원본을 반환한다.
    List<CallSttSegment> findSegmentsForFeedback(Long memberId, Long callId);
}
