package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;

@Service
@Slf4j
public class ConversationFeedbackEvaluationService {
    private final ChatClient chatClient;
    private final ConversationFeedbackEvaluationPromptBuilder promptBuilder;
    private final ConversationAnalysisValidator analysisValidator;
    private final ConversationFeedbackEvaluationValidator evaluationValidator;

    public ConversationFeedbackEvaluationService(ChatModel chatModel,
            ConversationFeedbackEvaluationPromptBuilder promptBuilder,
            ConversationAnalysisValidator analysisValidator,
            ConversationFeedbackEvaluationValidator evaluationValidator) {
        chatClient = ChatClient.builder(chatModel).build();
        this.promptBuilder = promptBuilder;
        this.analysisValidator = analysisValidator;
        this.evaluationValidator = evaluationValidator;
    }

    public ConversationFeedbackEvaluation evaluate(ConversationAnalysisInput input, ConversationAnalysisResult analysis) {
        analysisValidator.validateResult(input, analysis);
        var prompt = promptBuilder.build(input, analysis);
        ConversationFeedbackEvaluation result;
        try {
            result = chatClient.prompt(prompt)
                    .options(OpenAiChatOptions.builder().responseFormat(ResponseFormat.builder()
                            .jsonSchema(ConversationFeedbackEvaluationOutputSchema.forConversation(
                                    input.messages(), input.targetSpeaker())).build()))
                    .call().entity(ConversationFeedbackEvaluation.class);
        } catch (OpenAIServiceException exception) {
            throw failure(ErrorStatus.fromEvaluationApiError(exception.statusCode(), exception.code().orElse(null)));
        } catch (OpenAIIoException exception) {
            throw failure(isTimeout(exception) ? ErrorStatus.FEEDBACK_EVALUATION_API_TIMEOUT
                    : ErrorStatus.FEEDBACK_EVALUATION_API_CONNECTION_FAILED);
        } catch (JacksonException exception) {
            throw failure(ErrorStatus.FEEDBACK_EVALUATION_RESPONSE_CONVERSION_FAILED);
        } catch (OpenAIException exception) {
            throw failure(ErrorStatus.FEEDBACK_EVALUATION_API_FAILED);
        }
        return evaluationValidator.validateAndFilterStrengths(input.messages(), input.targetSpeaker(), result);
    }

    private static boolean isTimeout(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException) {
                return true;
            }
            if (cause == cause.getCause()) { break; }
        }
        return false;
    }

    private static BusinessException failure(ErrorStatus status) {
        // 키·대화·모델 응답·SDK 오류 본문은 기록하지 않는다.
        log.warn("Conversation evaluation failed: code={}", status.getCode());
        return new BusinessException(status);
    }
}
