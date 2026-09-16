package com.studiorent.tium.domain.chat.service.command;

import com.studiorent.tium.domain.chat.dto.ChatRequestDTO;
import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;

public interface ChatCommandService {

    ChatResponseDTO.CreateRoomResultDTO createRoom(Long memberId, ChatRequestDTO.CreateRoomDTO request);

    /**
     * 채팅방에 메시지를 보낸다. 저장, 방의 마지막 메시지 갱신, 보낸 사람의 읽음 포인터 이동이 한 트랜잭션이다.
     *
     * @param roomId 보낼 방. 참여자가 아니거나 상대가 나간 방이면 예외
     */
    ChatResponseDTO.SendMessageResultDTO sendMessage(Long memberId, Long roomId,
                                                     ChatRequestDTO.SendMessageDTO request);
}
