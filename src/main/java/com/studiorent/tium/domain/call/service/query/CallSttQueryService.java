package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.feedback.dto.ConversationTurn;

import java.util.List;

public interface CallSttQueryService {

    CallSttResponseDTO.SegmentList findStt(Long memberId, Long callId);

    CallSttResponseDTO.Status findSttStatus(Long memberId, Long callId);

    CallSttResponseDTO.Status getFeedbackStatus(Long memberId, Call call);

    List<ConversationTurn> findConversationForFeedback(Long memberId, Long callId);

    // 권한 및 양쪽 STT 준비 여부를 확인한 뒤 저장 ID를 포함하는 원본 세그먼트를 반환한다.
    List<CallSttSegment> findSegmentsForFeedback(Long memberId, Long callId);
}
