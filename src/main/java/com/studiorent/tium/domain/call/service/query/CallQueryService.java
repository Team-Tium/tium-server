package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;

public interface CallQueryService {

    CallSttResponseDTO.RecentCallList findRecentFeedbackCalls(Long memberId, Long cursor, int size);
}
