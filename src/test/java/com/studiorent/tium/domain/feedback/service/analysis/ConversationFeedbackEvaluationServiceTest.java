package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIServiceException;
import com.openai.errors.OpenAIIoException;
import com.studiorent.tium.domain.feedback.FeedbackTestData;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationFeedbackEvaluationServiceTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatModel model = mock(ChatModel.class);
    private final ConversationFeedbackEvaluationService service = new ConversationFeedbackEvaluationService(model,
            new ConversationFeedbackEvaluationPromptBuilder(mapper), new ConversationAnalysisValidator(FACTORY.getValidator()),
            new ConversationFeedbackEvaluationValidator(FACTORY.getValidator()));
    private final com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput input = FeedbackTestData.input(false);
    @AfterAll static void close() { FACTORY.close(); }
    @BeforeEach void defaults() { when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().build()); }
    private void responds(String value) {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(value)))));
    }
    private void error(ErrorStatus expected) {
        assertThatThrownBy(() -> service.evaluate(input, FeedbackTestData.analysis(input)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getBaseCode()).isEqualTo(expected));
    }
    @Test void 원문과_검증_분석을_전달하고_실제_사용자용_피드백을_반환한다() {
        var expected = FeedbackTestData.evaluation(input); responds(mapper.writeValueAsString(expected));
        assertThat(service.evaluate(input, FeedbackTestData.analysis(input))).isEqualTo(expected);
        var captor = ArgumentCaptor.forClass(Prompt.class); verify(model).call(captor.capture());
        var prompt = captor.getValue();
        // entity 변환기가 덧붙이는 형식 안내와 실제 대화 JSON을 구분한다.
        var payload = mapper.reader().without(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(prompt.getInstructions().stream()
                .map(message -> message.getText()).filter(text -> text.stripLeading().startsWith("{"))
                .findFirst().orElseThrow());
        assertThat(payload.get("conversation")).isEqualTo(mapper.valueToTree(input));
        assertThat(payload.get("analysis")).isEqualTo(mapper.valueToTree(FeedbackTestData.analysis(input)));
        var options = (OpenAiChatOptions) prompt.getOptions();
        assertThat(options.getTemperature()).isNull();
        assertThat(options.getResponseFormat().getJsonSchema()).isEqualTo(
                ConversationFeedbackEvaluationOutputSchema.forConversation(input.messages(), input.targetSpeaker()));
    }
    @Test void 분석이_검증되지_않으면_외부_평가를_호출하지_않는다() {
        clearInvocations(model);
        var invalid = new ConversationAnalysisResult("누락", List.of(input.messages().getFirst().messageId()), List.of(), List.of());
        assertThatThrownBy(() -> service.evaluate(input, invalid)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(model);
    }
    @Test void 잘못된_인용과_누락된_필수_값을_거절한다() {
        responds(mapper.writeValueAsString(FeedbackTestData.evaluation(input)).replace("그럼 내일 오전에 보내줘.", "없는 인용"));
        error(ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE);
        responds("{}"); error(ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE);
    }
    @Test void 변환_실패를_개별_오류로_처리한다() {
        responds("JSON이 아닌 응답"); error(ErrorStatus.FEEDBACK_EVALUATION_RESPONSE_CONVERSION_FAILED);
    }
    @ParameterizedTest
    @CsvSource({"401,invalid_api_key,FEEDBACK_EVALUATION_API_AUTH_FAILED", "403,forbidden,FEEDBACK_EVALUATION_API_PERMISSION_DENIED",
            "404,model_not_found,FEEDBACK_EVALUATION_MODEL_UNAVAILABLE", "429,insufficient_quota,FEEDBACK_EVALUATION_API_QUOTA_EXCEEDED",
            "429,rate_limit_exceeded,FEEDBACK_EVALUATION_API_RATE_LIMITED", "504,timeout,FEEDBACK_EVALUATION_API_TIMEOUT",
            "500,server_error,FEEDBACK_EVALUATION_API_FAILED"})
    void API_오류를_평가_단계의_ErrorStatus로_반환한다(int status, String code, ErrorStatus expected) {
        var exception = mock(OpenAIServiceException.class); when(exception.statusCode()).thenReturn(status);
        when(exception.code()).thenReturn(Optional.of(code)); when(model.call(any(Prompt.class))).thenThrow(exception);
        error(expected);
    }
    @Test void 타임아웃과_연결_실패를_구분한다() {
        when(model.call(any(Prompt.class))).thenThrow(new OpenAIIoException("timeout", new SocketTimeoutException()));
        error(ErrorStatus.FEEDBACK_EVALUATION_API_TIMEOUT);
        when(model.call(any(Prompt.class))).thenThrow(new OpenAIIoException("connection", new IOException()));
        error(ErrorStatus.FEEDBACK_EVALUATION_API_CONNECTION_FAILED);
    }
}
