package com.studiorent.tium.domain.matching.service.command;

import com.studiorent.tium.domain.matching.dto.MatchingRequestDTO;

public interface MatchingCommandService {

    void enqueue(Long memberId, MatchingRequestDTO.EnqueueDTO request);

    void cancel(Long memberId);
}
