package com.studiorent.tium.domain.chat.service.command;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;

public interface ChatCommandService {

    ChatResponseDTO.CreateRoomResultDTO createRoom(Long memberId, ChatRequestDTO.CreateRoomDTO request);
}
