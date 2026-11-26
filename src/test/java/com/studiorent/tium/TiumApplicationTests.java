package com.studiorent.tium;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.converter.BeanOutputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.FeedbackTestData;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisPromptBuilder;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisService;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationAnalysisValidator;
import com.studiorent.tium.domain.feedback.service.analysis.ConversationFeedbackEvaluationPromptBuilder;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import jakarta.validation.Validation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import tools.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
class TiumApplicationTests {

    @Autowired ApplicationContext context;
    @Autowired ChatModel chatModel;
    @Value("${spring.ai.openai.chat.model}") String configuredModel;

    @Test
    void contextLoads() {
        assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo(configuredModel);
        assertThat(context.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
        assertThat(context.containsBean("retrievalAugmentationAdvisor")).isFalse();
        assertThat(context.containsBean("initEtlPipeline")).isFalse();
        assertThat(context.containsBean("vectorStore")).isFalse();
    }

    @Test
    void 모델_JSON_변환_실패시_응답_원문을_로그에_남기지_않는다() {
        String privateResponse = "PRIVATE-CONVERSATION-MUST-NOT-BE-LOGGED";
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            var converter = new BeanOutputConverter<>(ConversationFeedbackEvaluation.class);
            assertThatThrownBy(() -> converter.convert(privateResponse)).isInstanceOf(RuntimeException.class);
            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(privateResponse));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 빈_choices_응답의_라이브러리_WARN에_대화와_1차_분석을_남기지_않는다() {
        String conversationText = "PRIVATE-CONVERSATION-SENTINEL";
        String analysisText = "PRIVATE-ANALYSIS-SENTINEL";
        var baseInput = FeedbackTestData.input(false);
        var input = new ConversationAnalysisInput(baseInput.relationship(), baseInput.targetSpeaker(), List.of(
                baseInput.messages().getFirst(), new ConversationAnalysisInput.Utterance(
                        baseInput.messages().getLast().messageId(), baseInput.targetSpeaker(), conversationText)));
        var baseAnalysis = FeedbackTestData.analysis(input);
        var analysis = new ConversationAnalysisResult(analysisText, baseAnalysis.contextEvidenceMessageIds(),
                baseAnalysis.utterances(), baseAnalysis.uncertainties());
        var client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(ChatCompletion.builder().id("test-empty").created(1L)
                        .model("test-model").choices(List.of()).build());
        var model = mockClientModel(client);
        var prompt = new ConversationFeedbackEvaluationPromptBuilder(new ObjectMapper()).build(input, analysis);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            assertThat(model.call(prompt).getResults()).isEmpty();
            verify(client.chat().completions()).create(any(ChatCompletionCreateParams.class));
            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(conversationText)
                    || event.getFormattedMessage().contains(analysisText));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 라이브러리_WARN을_차단해도_애플리케이션의_본문없는_오류코드_로그는_유지한다() {
        var client = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        var failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(401);
        when(failure.code()).thenReturn(Optional.of("invalid_api_key"));
        when(client.chat().completions().create(any(ChatCompletionCreateParams.class))).thenThrow(failure);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new ConversationAnalysisService(mockClientModel(client),
                    new ConversationAnalysisPromptBuilder(new ObjectMapper()),
                    new ConversationAnalysisValidator(factory.getValidator()));
            assertThatThrownBy(() -> service.analyze(FeedbackTestData.input(false)))
                    .isInstanceOfSatisfying(BusinessException.class,
                            error -> assertThat(error.getBaseCode()).isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_API_AUTH_FAILED));
            assertThat(appender.list).anyMatch(event -> event.getLoggerName().equals(ConversationAnalysisService.class.getName())
                    && event.getFormattedMessage().contains("code=FEEDBACK5014")
                    && event.getFormattedMessage().contains("upstreamStatus=401"));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    private OpenAiChatModel mockClientModel(OpenAIClient client) {
        return OpenAiChatModel.builder().openAiClient(client).openAiClientAsync(mock(OpenAIClientAsync.class))
                .options(OpenAiChatOptions.builder().model("test-model").build()).build();
    }

}
