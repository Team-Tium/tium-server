package com.studiorent.tium.domain.chat.service.query;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;

public interface ChatSuggestionService {

    ChatResponseDTO.SuggestRepliesDTO suggestReplies(Long memberId, Long roomId);
}
