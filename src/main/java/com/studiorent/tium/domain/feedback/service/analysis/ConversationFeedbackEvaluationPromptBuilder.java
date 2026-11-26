package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 2차 평가와 사용자용 문체를 같은 지침으로 생성한다. 호출·검증은 수행하지 않는다. */
@Component
public class ConversationFeedbackEvaluationPromptBuilder {
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public ConversationFeedbackEvaluationPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        try {
            systemPrompt = new ClassPathResource("ai/conversation-evaluation-system.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Conversation evaluation prompt could not be loaded", exception);
        }
    }

    public Prompt build(ConversationAnalysisInput conversation, ConversationAnalysisResult analysis) {
        return new Prompt(List.of(new SystemMessage(systemPrompt),
                new UserMessage(objectMapper.writeValueAsString(new Payload(conversation, analysis)))));
    }

    private record Payload(ConversationAnalysisInput conversation, ConversationAnalysisResult analysis) {
    }
}
