package com.studiorent.tium.domain.matching.service.command;

import com.studiorent.tium.domain.matching.dto.MatchingRequestDTO;
import com.studiorent.tium.domain.matching.dto.MatchingResponseDTO;

public interface MatchingCommandService {

    MatchingResponseDTO.EnqueueResultDTO enqueue(Long memberId, MatchingRequestDTO.EnqueueDTO request);

    void cancel(Long memberId);
}
