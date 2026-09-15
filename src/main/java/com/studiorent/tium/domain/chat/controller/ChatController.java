package com.studiorent.tium.domain.chat.controller;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.service.command.ChatCommandService;
import com.studiorent.tium.domain.chat.service.query.ChatQueryService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "채팅 API")
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatCommandService chatCommandService;
    private final ChatQueryService chatQueryService;

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

    @Operation(summary = "최근 대화한 채팅방 목록",
            description = "마지막 메시지 ID 내림차순으로 내려온다. 내가 나간 방과 "
                    + "메시지가 하나도 없는 방은 제외된다. 다음 페이지는 응답의 nextCursor를 cursor로 넘긴다.")
    @GetMapping
    public ApiResponse<ChatResponseDTO.GetChatDTO> getChats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.onSuccess(
                chatQueryService.getChats(userDetails.getMemberId(), cursor, clampSize(size)));
    }

    @Operation(summary = "채팅 내역 조회",
            description = "cursor는 과거 방향(최신순), after는 미래 방향(오래된 순)이다. "
                    + "둘 다 오면 after가 우선한다. after는 소켓이 끊긴 동안 온 메시지를 채우는 용도다.")
    @GetMapping("/{roomId}/messages")
    public ApiResponse<ChatResponseDTO.GetMessagesDTO> getMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long after,
            @RequestParam(defaultValue = "30") int size) {

        return ApiResponse.onSuccess(
                chatQueryService.getMessages(userDetails.getMemberId(), roomId,
                        cursor, after, clampSize(size)));
    }

    /** 명세상 최대 100이다. 음수나 0이 들어오면 PageRequest가 터지므로 아래도 막는다. */
    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }
}
