package com.studiorent.tium.domain.chat.service.query;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatSuggestionServiceImpl implements ChatSuggestionService {

    private static final int RECENT_MESSAGE_LIMIT = 20;

    private static final String SYSTEM_PROMPT = """
            너는 사용자가 채팅에서 자연스럽게 다음 말을 이어가도록 돕는 답장 추천 도우미이다.

            원칙:
            - 사용자가 바로 보낼 수 있는 한국어 문장만 추천한다.
            - 상대가 마지막으로 말한 내용과 현재 흐름을 가장 중요하게 본다.
            - 부담스럽거나 과하게 친한 표현은 피한다.
            - 상대의 감정, 관심사, 질문을 자연스럽게 이어간다.
            - 추천은 서로 다른 방향의 3개 문장으로 만든다.
            - 각 문장은 1~2문장으로 짧게 쓴다.
            - 마지막 상대 메시지에 직접 반응해라
            - 상대가 감정을 표현하면 먼저 인정해라
            - 걱정 혹은 고민이라면 바로 해결책이나 응원으로 끝내지 마라
            - 상대가 말하고자 하는 핵심 요지를 짚어주는 문장을 1개 이상 포함해라.
            """;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomValidator chatRoomValidator;
    private final ChatClient chatClient;

    public ChatSuggestionServiceImpl(ChatMessageRepository chatMessageRepository,
                                     ChatRoomValidator chatRoomValidator,
                                     ChatClient.Builder chatClientBuilder) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomValidator = chatRoomValidator;
        this.chatClient = chatClientBuilder
                .defaultOptions(ChatOptions.builder().temperature(0.7))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatResponseDTO.SuggestRepliesDTO suggestReplies(Long memberId, Long roomId) {
        chatRoomValidator.getJoinedMember(roomId, memberId);

        List<ChatMessage> messages = getRecentTextMessages(roomId);
        String conversation = toPromptConversation(memberId, messages);

        SuggestRepliesResult result = chatClient.prompt(createPrompt(conversation))
                .call()
                .entity(SuggestRepliesResult.class);

        if (result == null || result.suggestions() == null || result.suggestions().isEmpty()) {
            throw new BusinessException(ErrorStatus.CHAT_SUGGESTION_INVALID_RESPONSE);
        }

        return new ChatResponseDTO.SuggestRepliesDTO(result.suggestions());
    }

    private List<ChatMessage> getRecentTextMessages(Long roomId) {
        Pageable limit = PageRequest.of(0, RECENT_MESSAGE_LIMIT);
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findByChatRoomIdOrderByIdDesc(roomId, limit));
        Collections.reverse(messages);

        List<ChatMessage> textMessages = messages.stream()
                .filter(message -> message.getMessageType() == MessageType.TEXT)
                .filter(message -> !message.isDeleted())
                .toList();

        if (textMessages.isEmpty()) {
            throw new BusinessException(ErrorStatus.CHAT_CONVERSATION_EMPTY);
        }

        return textMessages;
    }

    private String toPromptConversation(Long memberId, List<ChatMessage> messages) {
        StringBuilder conversation = new StringBuilder();

        // LLM이 화자를 헷갈리지 않도록 현재 사용자는 me, 상대는 other로 고정한다.
        for (ChatMessage message : messages) {
            conversation.append(message.isSentBy(memberId) ? "me" : "other")
                    .append(": ")
                    .append(message.getContent())
                    .append(System.lineSeparator());
        }

        return conversation.toString();
    }

    private Prompt createPrompt(String conversation) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage("""
                아래는 최근 채팅 흐름이다.
                이 다음에 me가 보낼 만한 답장 3개를 suggestions 배열로만 추천해라.

                대화:
                %s
                """.formatted(conversation)));

        return Prompt.builder().messages(messages).build();
    }

    private record SuggestRepliesResult(List<String> suggestions) {
    }
}
