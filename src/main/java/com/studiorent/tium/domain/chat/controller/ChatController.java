package com.studiorent.tium.domain.chat.controller;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 API")
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatCommandService chatCommandService;

    @Operation(summary = "채팅방 생성",
            description = "상대와의 활성 채팅방을 확보합니다. 이미 있으면 새로 만들지 않고 그 방을 반환하며(created=false), "
                    + "한쪽이라도 나간 방밖에 없으면 새 방을 만듭니다(created=true). "
                    + "방을 만든 직후에는 메시지가 없어 채팅방 목록에는 아직 나타나지 않습니다.")
    @PostMapping
    public ApiResponse<ChatResponseDTO.CreateRoomResultDTO> createRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ChatRequestDTO.CreateRoomDTO request) {

        return ApiResponse.onSuccess(chatCommandService.createRoom(userDetails.getMemberId(), request));
    }
}
