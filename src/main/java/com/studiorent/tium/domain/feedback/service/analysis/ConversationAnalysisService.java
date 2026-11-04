package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;

/**
 * 첫 번째 LLM 호출을 담당한다. 검증을 통과한 분석 결과만 반환한다.
 */
@Service
@Slf4j
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

        ConversationAnalysisResult result = requestAnalysis(prompt);

        // JSON 변환에 성공했더라도 원문 근거가 잘못된 결과는 반환하지 않는다.
        validator.validateResult(input, result);
        return result;
    }

    private ConversationAnalysisResult requestAnalysis(Prompt prompt) {
        try {
            return chatClient.prompt(prompt).call().entity(ConversationAnalysisResult.class);
        } catch (OpenAIServiceException exception) {
            ErrorStatus errorStatus = ErrorStatus.fromAnalysisApiError(
                    exception.statusCode(), exception.code().orElse(null));
            // 외부 오류 메시지, 키, 대화 및 응답 본문은 로그에 포함하지 않는다.
            log.warn("Conversation analysis API failed: code={}, upstreamStatus={}",
                    errorStatus.getCode(), exception.statusCode());
            throw new BusinessException(errorStatus);
        } catch (OpenAIIoException exception) {
            ErrorStatus errorStatus = isTimeout(exception)
                    ? ErrorStatus.FEEDBACK_ANALYSIS_API_TIMEOUT
                    : ErrorStatus.FEEDBACK_ANALYSIS_API_CONNECTION_FAILED;
            throw failure(errorStatus);
        } catch (JacksonException exception) {
            // DTO 변환 오류만 처리한다. 서버 입력의 직렬화는 이 try 범위 밖이다.
            throw failure(ErrorStatus.FEEDBACK_ANALYSIS_RESPONSE_CONVERSION_FAILED);
        } catch (OpenAIException exception) {
            throw failure(ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED);
        }
    }

    private boolean isTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof HttpTimeoutException) {
                return true;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return false;
    }

    private BusinessException failure(ErrorStatus errorStatus) {
        log.warn("Conversation analysis failed: code={}", errorStatus.getCode());
        return new BusinessException(errorStatus);
    }
}
