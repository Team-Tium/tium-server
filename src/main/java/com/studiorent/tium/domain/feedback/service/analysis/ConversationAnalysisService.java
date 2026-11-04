package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * 첫 번째 LLM 호출을 담당한다. 검증을 통과한 분석 결과만 반환한다.
 */
@Service
public class ConversationAnalysisService {

    private final ChatClient chatClient;
    private final ConversationAnalysisPromptBuilder promptBuilder;
    private final ConversationAnalysisValidator validator;

    public ConversationAnalysisService(
            ChatModel chatModel,
            ConversationAnalysisPromptBuilder promptBuilder,
            ConversationAnalysisValidator validator
    ) {
        // 독립적인 빌더를 사용하여 피드백용 RAG Advisor나 대화 메모리를 붙이지 않는다.
        this.chatClient = ChatClient.builder(chatModel)
                .defaultOptions(ChatOptions.builder().temperature(0.0))
                .build();
        this.promptBuilder = promptBuilder;
        this.validator = validator;
    }

    public ConversationAnalysisResult analyze(ConversationAnalysisInput input) {
        // 잘못된 입력은 외부 API를 호출하기 전에 거절한다.
        validator.validateInput(input);
        Prompt prompt = promptBuilder.build(input);

        ConversationAnalysisResult result = chatClient.prompt(prompt)
                .call()
                .entity(ConversationAnalysisResult.class);

        // JSON 변환에 성공했더라도 원문 근거가 잘못된 결과는 반환하지 않는다.
        validator.validateResult(input, result);
        return result;
    }
}
