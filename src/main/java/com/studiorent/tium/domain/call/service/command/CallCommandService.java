package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallRequestDTO;
import com.studiorent.tium.domain.call.dto.CallResponseDTO;

public interface CallCommandService {

    CallResponseDTO.CallResultDTO startCall(Long memberId, CallRequestDTO.CallStartDTO request);

    CallResponseDTO.CallResultDTO endCall(Long memberId, Long callId);
}
