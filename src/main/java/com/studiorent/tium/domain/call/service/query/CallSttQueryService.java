package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.feedback.dto.ConversationTurn;

import java.util.List;

public interface CallSttQueryService {

    CallSttResponseDTO.SegmentList findStt(Long memberId, Long callId);

    CallSttResponseDTO.Status findSttStatus(Long memberId, Long callId);

    CallSttResponseDTO.Status getFeedbackStatus(Long memberId, Call call);

    List<ConversationTurn> findConversationForFeedback(Long memberId, Long callId);
}
