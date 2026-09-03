package com.studiorent.tium.domain.auth.service.command;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;

public interface AuthCommandService {

    AuthResponseDTO.LoginResultDTO login(String provider, AuthRequestDTO.LoginDTO request);
}
