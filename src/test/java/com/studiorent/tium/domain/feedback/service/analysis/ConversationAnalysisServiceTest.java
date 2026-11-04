package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationAnalysisServiceTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ChatModel chatModel = mock(ChatModel.class);
    private final ConversationAnalysisService service = new ConversationAnalysisService(
            chatModel,
            new ConversationAnalysisPromptBuilder(new ObjectMapper()),
            new ConversationAnalysisValidator(FACTORY.getValidator())
    );

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @BeforeEach
    void configureModelOptions() {
        when(chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());
    }

    private ConversationAnalysisInput input() {
        return new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.ME,
                List.of(new Utterance("m1", Speaker.ME, "요즘 회사 때문에 힘들어요.")));
    }

    private void modelResponds(String quote) {
        String json = """
                {
                  "contextSummary": "사용자가 회사와 관련한 어려움을 표현한다.",
                  "contextEvidenceMessageIds": ["m1"],
                  "utterances": [{
                    "messageId": "m1",
                    "purposeCandidates": [{
                      "purpose": "EMOTION_EXPRESSION",
                      "evidenceQuote": "%s",
                      "reason": "힘든 감정을 직접 표현한다."
                    }],
                    "explicitEmotionQuotes": ["힘들어요."],
                    "explicitRequestQuotes": [],
                    "uncertainties": ["조언을 원하는지는 명시되지 않았다."]
                  }],
                  "uncertainties": []
                }
                """.formatted(quote);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }

    @Test
    void 모델의_JSON을_변환하고_검증된_분석을_반환한다() {
        modelResponds("힘들어요.");

        var result = service.analyze(input());

        assertThat(result.utterances()).hasSize(1);
        assertThat(result.utterances().getFirst().messageId()).isEqualTo("m1");
        assertThat(result.utterances().getFirst().purposeCandidates().getFirst().evidenceQuote())
                .isEqualTo("힘들어요.");

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        var prompt = captor.getValue();
        // RAG 검색 없이 분석 지침과 원본 대화가 모델에 전달되는지 확인한다.
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().getLast()).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().getLast().getText()).contains("요즘 회사 때문에 힘들어요.");
        assertThat(prompt.getOptions().getTemperature()).isEqualTo(0.0);
    }

    @Test
    void 잘못된_입력이면_모델을_호출하지_않는다() {
        // 서비스 생성 중 모델 설정을 읽을 가능성과 API 호출 검증을 구분한다.
        org.mockito.Mockito.clearInvocations(chatModel);

        assertThatThrownBy(() -> service.analyze(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getBaseCode()).isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT));

        verifyNoInteractions(chatModel);
    }

    @Test
    void 원문에_없는_인용문을_포함한_분석은_반환하지_않는다() {
        modelResponds("너무 슬퍼요.");

        assertThatThrownBy(() -> service.analyze(input()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getBaseCode())
                                .isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_PURPOSE_QUOTE_NOT_FOUND));
    }

    private void assertMappedError(ErrorStatus expected) {
        assertThatThrownBy(() -> service.analyze(input()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getBaseCode()).isEqualTo(expected));
    }

    static Stream<Arguments> apiErrors() {
        return Stream.of(
                Arguments.of(401, "invalid_api_key", ErrorStatus.FEEDBACK_ANALYSIS_API_AUTH_FAILED),
                Arguments.of(403, null, ErrorStatus.FEEDBACK_ANALYSIS_API_PERMISSION_DENIED),
                Arguments.of(404, "model_not_found", ErrorStatus.FEEDBACK_ANALYSIS_MODEL_UNAVAILABLE),
                Arguments.of(404, null, ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED),
                Arguments.of(429, "rate_limit_exceeded", ErrorStatus.FEEDBACK_ANALYSIS_API_RATE_LIMITED),
                Arguments.of(429, "insufficient_quota", ErrorStatus.FEEDBACK_ANALYSIS_API_QUOTA_EXCEEDED),
                Arguments.of(408, null, ErrorStatus.FEEDBACK_ANALYSIS_API_TIMEOUT),
                Arguments.of(504, null, ErrorStatus.FEEDBACK_ANALYSIS_API_TIMEOUT),
                Arguments.of(500, null, ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED),
                Arguments.of(503, null, ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED),
                Arguments.of(400, null, ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED)
        );
    }

    @ParameterizedTest
    @MethodSource("apiErrors")
    void API_상태와_원인에_맞는_개별_오류를_반환한다(int status, String code, ErrorStatus expected) {
        var exception = mock(OpenAIServiceException.class);
        when(exception.statusCode()).thenReturn(status);
        when(exception.code()).thenReturn(Optional.ofNullable(code));
        when(chatModel.call(any(Prompt.class))).thenThrow(exception);

        assertMappedError(expected);
    }

    @Test
    void 네트워크_타임아웃을_별도_오류로_변환한다() {
        when(chatModel.call(any(Prompt.class))).thenThrow(
                new OpenAIIoException("test timeout", new SocketTimeoutException()));

        assertMappedError(ErrorStatus.FEEDBACK_ANALYSIS_API_TIMEOUT);
    }

    @Test
    void 일반_연결_실패를_타임아웃과_구분한다() {
        when(chatModel.call(any(Prompt.class))).thenThrow(
                new OpenAIIoException("test connection failure", new IOException()));

        assertMappedError(ErrorStatus.FEEDBACK_ANALYSIS_API_CONNECTION_FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JSON이 아닌 응답", "{\"utterances\":[{\"purposeCandidates\":[{\"purpose\":\"UNDEFINED_PURPOSE\"}]}]}"})
    void 잘못된_JSON과_enum은_응답_변환_오류로_처리한다(String response) {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(response)))));

        assertMappedError(ErrorStatus.FEEDBACK_ANALYSIS_RESPONSE_CONVERSION_FAILED);
    }

    @Test
    void JSON_null은_기존_구조_검증_오류로_처리한다() {
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("null")))));

        assertMappedError(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
    }

    @Test
    void 예상하지_못한_프로그램_오류를_API_오류로_감추지_않는다() {
        var bug = new IllegalStateException("test programming failure");
        when(chatModel.call(any(Prompt.class))).thenThrow(bug);

        assertThatThrownBy(() -> service.analyze(input())).isSameAs(bug);
    }
}
