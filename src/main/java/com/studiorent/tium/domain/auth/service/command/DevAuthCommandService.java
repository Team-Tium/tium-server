package com.studiorent.tium.domain.auth.service.command;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;

public interface DevAuthCommandService {

    AuthResponseDTO.DevTokenResultDTO issueToken(AuthRequestDTO.DevTokenDTO request);

    AuthResponseDTO.DevVerifyResultDTO verifyToken(AuthRequestDTO.DevVerifyDTO request);
}
