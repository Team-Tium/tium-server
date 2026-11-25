package com.studiorent.tium.domain.feedback;

import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.feedback.controller.FeedbackController;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.domain.feedback.service.analysis.*;
import com.studiorent.tium.domain.feedback.service.command.FeedbackCommandServiceImpl;
import com.studiorent.tium.domain.feedback.service.query.ConversationAnalysisInputQueryService;
import com.studiorent.tium.domain.feedback.service.query.FeedbackQueryService;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import com.studiorent.tium.global.security.CustomUserDetails;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 실제 컨트롤러→command→분석→평가→변환 경로. DB와 외부 모델은 대체한다. */
class FeedbackApiTwoStageTest {
    private static final jakarta.validation.ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Long CLAIM_ID = 101L;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatModel model = mock(ChatModel.class);
    private final FeedbackRepository repository = mock(FeedbackRepository.class);
    private final ConversationAnalysisInputQueryService inputs = mock(ConversationAnalysisInputQueryService.class);
    private final CallAccessValidator access = mock(CallAccessValidator.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ConversationAnalysisValidator validator = new ConversationAnalysisValidator(FACTORY.getValidator());
    private final ConversationFeedbackService pipeline = new ConversationFeedbackService(
            new ConversationAnalysisService(model, new ConversationAnalysisPromptBuilder(mapper), validator),
            new ConversationFeedbackEvaluationService(model, new ConversationFeedbackEvaluationPromptBuilder(mapper),
                    validator, new ConversationFeedbackEvaluationValidator(FACTORY.getValidator())));
    private final FeedbackCommandServiceImpl command = new FeedbackCommandServiceImpl(repository, inputs, pipeline, access, transactions);
    private final List<Boolean> savedCompletionStates = new ArrayList<>();
    private Feedback savedCallClaim;
    private Feedback managedCallClaim;
    private MockMvc mvc;

    @AfterAll static void close() { FACTORY.close(); }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    @BeforeEach void setup() {
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(repository.save(any(Feedback.class))).thenAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            savedCompletionStates.add(feedback.getCompleted());
            if (feedback.getCallId() != null && Boolean.FALSE.equals(feedback.getCompleted())) {
                savedCallClaim = Feedback.builder().id(CLAIM_ID).memberId(feedback.getMemberId())
                        .callId(feedback.getCallId()).completed(false).build();
                managedCallClaim = Feedback.builder().id(CLAIM_ID).memberId(feedback.getMemberId())
                        .callId(feedback.getCallId()).completed(false).build();
                return savedCallClaim;
            }
            return feedback;
        });
        when(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L))
                .thenAnswer(invocation -> Optional.ofNullable(managedCallClaim));
        when(repository.findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(1L, 10L)).thenReturn(Optional.empty());
        var principal = mock(CustomUserDetails.class); when(principal.getMemberId()).thenReturn(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new FeedbackController(command, mock(FeedbackQueryService.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
    private ChatResponse response(Object value) {
        return responseText(mapper.writeValueAsString(value));
    }
    private ChatResponse responseText(String value) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(value))));
    }
    private tools.jackson.databind.JsonNode userPayload(Prompt prompt) {
        return mapper.reader().without(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(prompt.getInstructions().stream().map(message -> message.getText())
                        .filter(text -> text.stripLeading().startsWith("{")).findFirst().orElseThrow());
    }
    private void successfulModel(boolean call) {
        var input = FeedbackTestData.input(call);
        if (call) when(inputs.findCallInput(1L, 10L, RelationShip.UNCONFIRMED)).thenReturn(input);
        else when(inputs.findChatInput(1L, 10L, RelationShip.UNCONFIRMED)).thenReturn(input);
        when(model.call(any(Prompt.class))).thenReturn(response(FeedbackTestData.analysis(input)), response(FeedbackTestData.evaluation(input)));
    }
    @Test void 채팅_API가_2단계_사용자용_피드백을_기존_형식으로_반환하고_저장한다() throws Exception {
        successfulModel(false);
        mvc.perform(post("/chats/10/feedback")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.overallQuality").value("GOOD"))
                .andExpect(jsonPath("$.result.overallFeedback").value(FeedbackTestData.evaluation(FeedbackTestData.input(false)).feedback()))
                .andExpect(jsonPath("$.result.flowProblem").isEmpty())
                .andExpect(jsonPath("$.result.practicePoint").value(""));
        verify(model, times(2)).call(any(Prompt.class));
        verify(inputs).findChatInput(1L, 10L, RelationShip.UNCONFIRMED);
        assertThat(savedCompletionStates).containsExactly(true);
        verify(repository).save(argThat(f -> f.getRoomId().equals(10L) && f.getCallId() == null && f.getMemberId().equals(1L)));
    }
    @Test void 통화_API는_같은_2단계를_사용하고_평가_범위와_완료_상태를_저장한다() throws Exception {
        successfulModel(true);
        mvc.perform(post("/api/v1/call/10/feedback")).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.overallQuality").value("GOOD"))
                .andExpect(jsonPath("$.result.overallFeedback").value(org.hamcrest.Matchers.containsString("최근 최대 200개 발화")));
        verify(model, times(2)).call(any(Prompt.class));
        assertThat(savedCompletionStates).containsExactly(false);
        assertThat(savedCallClaim.getCompleted()).isFalse();
        assertThat(managedCallClaim.getCompleted()).isTrue();
        assertThat(managedCallClaim.getOverallFeedback()).contains("최근 최대 200개 발화");
        var order = inOrder(access, repository);
        order.verify(access).validateParticipantForUpdate(10L, 1L);
        order.verify(repository).save(any(Feedback.class));
        order.verify(access).validateParticipantForUpdate(10L, 1L);
        order.verify(repository).findByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L);
        verify(repository, never()).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "JSON이 아닌 응답", "{}"})
    void 분석_출력만_잘못되면_분석만_한번_재시도한_후_평가하고_저장한다(String invalidOutput) {
        successfulModel(false);
        var input = FeedbackTestData.input(false);
        when(model.call(any(Prompt.class))).thenReturn(responseText(invalidOutput),
                response(FeedbackTestData.analysis(input)), response(FeedbackTestData.evaluation(input)));

        assertThat(command.createFeedback(1L, 10L).overallQuality()).isEqualTo("GOOD");

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(captor.capture());
        assertThat(userPayload(captor.getAllValues().get(0))).isEqualTo(userPayload(captor.getAllValues().get(1)));
        assertThat(userPayload(captor.getAllValues().get(2)).get("analysis"))
                .isEqualTo(mapper.valueToTree(FeedbackTestData.analysis(input)));
        assertThat(savedCompletionStates).containsExactly(true);
    }

    @Test void 분석의_근거_참조_검증_실패도_분석_단계만_재시도한다() {
        successfulModel(false);
        var input = FeedbackTestData.input(false);
        var analysis = FeedbackTestData.analysis(input);
        var invalid = new com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult(
                analysis.contextSummary(), List.of("missing"), analysis.utterances(), analysis.uncertainties());
        when(model.call(any(Prompt.class))).thenReturn(response(invalid), response(analysis), response(FeedbackTestData.evaluation(input)));

        assertThat(command.createFeedback(1L, 10L).overallQuality()).isEqualTo("GOOD");

        verify(model, times(3)).call(any(Prompt.class));
        assertThat(savedCompletionStates).containsExactly(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "JSON이 아닌 응답", "{}"})
    void 평가_출력만_잘못되면_검증된_동일_분석으로_평가만_한번_재시도한다(String invalidOutput) {
        successfulModel(true);
        var input = FeedbackTestData.input(true);
        when(model.call(any(Prompt.class))).thenReturn(response(FeedbackTestData.analysis(input)),
                responseText(invalidOutput), response(FeedbackTestData.evaluation(input)));

        assertThat(command.createCallFeedback(1L, 10L).overallQuality()).isEqualTo("GOOD");

        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(3)).call(captor.capture());
        var firstEvaluation = userPayload(captor.getAllValues().get(1));
        var retriedEvaluation = userPayload(captor.getAllValues().get(2));
        assertThat(firstEvaluation).isEqualTo(retriedEvaluation);
        assertThat(retriedEvaluation.get("analysis")).isEqualTo(mapper.valueToTree(FeedbackTestData.analysis(input)));
        assertThat(savedCompletionStates).containsExactly(false);
        assertThat(managedCallClaim.getCompleted()).isTrue();
        verify(repository, never()).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(anyLong(), anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 두_단계가_각각_한번_재시도하면_총_4회_호출한_뒤_완료한다(boolean call) {
        successfulModel(call);
        var input = FeedbackTestData.input(call);
        when(model.call(any(Prompt.class))).thenReturn(response(null), response(FeedbackTestData.analysis(input)),
                response(null), response(FeedbackTestData.evaluation(input)));

        var result = call ? command.createCallFeedback(1L, 10L) : command.createFeedback(1L, 10L);

        assertThat(result.overallQuality()).isEqualTo("GOOD");
        verify(model, times(4)).call(any(Prompt.class));
        assertThat(savedCompletionStates).containsExactly(!call);
        if (call) assertThat(managedCallClaim.getCompleted()).isTrue();
    }

    @Test void 서버_분석_입력이_잘못되면_재시도하거나_모델을_호출하지_않는다() {
        clearInvocations(model);

        assertThatThrownBy(() -> pipeline.generate(null)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getBaseCode()).isEqualTo(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_INPUT));

        verifyNoInteractions(model);
        assertThat(savedCompletionStates).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorStatus.class, names = {
            "FEEDBACK_ANALYSIS_API_AUTH_FAILED", "FEEDBACK_ANALYSIS_API_RATE_LIMITED",
            "FEEDBACK_ANALYSIS_API_TIMEOUT", "FEEDBACK_ANALYSIS_API_CONNECTION_FAILED",
            "FEEDBACK_EVALUATION_API_AUTH_FAILED", "FEEDBACK_EVALUATION_API_RATE_LIMITED",
            "FEEDBACK_EVALUATION_API_TIMEOUT", "FEEDBACK_EVALUATION_API_CONNECTION_FAILED"})
    void API_오류는_단계와_관계없이_재시도하지_않는다(ErrorStatus errorStatus) {
        successfulModel(false);
        var failure = new BusinessException(errorStatus);
        boolean evaluationFailed = errorStatus.name().startsWith("FEEDBACK_EVALUATION_");
        if (evaluationFailed) {
            when(model.call(any(Prompt.class))).thenReturn(response(FeedbackTestData.analysis(FeedbackTestData.input(false))))
                    .thenThrow(failure);
        } else {
            when(model.call(any(Prompt.class))).thenThrow(failure);
        }

        assertThatThrownBy(() -> command.createFeedback(1L, 10L)).isSameAs(failure);

        verify(model, times(evaluationFailed ? 2 : 1)).call(any(Prompt.class));
        assertThat(savedCompletionStates).isEmpty();
    }
    @Test void 기존_완료_통화_피드백은_재호출없이_반환한다() {
        var feedback = Feedback.builder().memberId(1L).callId(10L).completed(true)
                .overallQuality(com.studiorent.tium.domain.feedback.entity.enums.OverallQuality.GOOD)
                .overallFeedback("저장된 사용자용 피드백이에요.").build();
        when(repository.findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(1L, 10L)).thenReturn(Optional.of(feedback));
        clearInvocations(model);
        assertThat(command.createCallFeedback(1L, 10L).overallFeedback()).isEqualTo(feedback.getOverallFeedback());
        verifyNoInteractions(model, inputs); assertThat(savedCompletionStates).isEmpty();
    }
    @Test void 진행중인_통화_요청은_중복으로_차단하며_다른_요청의_선점은_지우지_않는다() {
        when(repository.existsByMemberIdAndCallIdAndCompletedFalse(1L, 10L)).thenReturn(true);
        clearInvocations(model);
        assertThatThrownBy(() -> command.createCallFeedback(1L, 10L)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getBaseCode()).isEqualTo(ErrorStatus.DUPLICATE_REQUEST));
        verifyNoInteractions(model, inputs);
        verify(repository, never()).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(anyLong(), anyLong(), anyLong());
    }
    @Test void 입력_권한_실패면_분석과_저장을_진행하지_않는다() {
        when(inputs.findChatInput(1L, 10L, RelationShip.UNCONFIRMED)).thenThrow(new BusinessException(ErrorStatus.CHAT_NOT_ROOM_MEMBER));
        clearInvocations(model);
        assertThatThrownBy(() -> command.createFeedback(1L, 10L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(model); assertThat(savedCompletionStates).isEmpty();
    }
    @Test void 채팅의_1차가_재시도에도_실패하면_2차와_완료_저장을_막는다() {
        successfulModel(false); when(model.call(any(Prompt.class))).thenReturn(response(null));
        assertThatThrownBy(() -> command.createFeedback(1L, 10L)).isInstanceOf(BusinessException.class);
        verify(model, times(2)).call(any(Prompt.class)); assertThat(savedCompletionStates).isEmpty();
    }
    @Test void 통화의_1차가_재시도에도_실패하면_2차를_호출하지_않고_선점을_해제한다() {
        successfulModel(true); when(model.call(any(Prompt.class))).thenReturn(response(null));
        assertThatThrownBy(() -> command.createCallFeedback(1L, 10L)).isInstanceOf(BusinessException.class);
        verify(model, times(2)).call(any(Prompt.class)); assertThat(savedCompletionStates).containsExactly(false);
        verify(repository).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L);
    }
    @Test void 통화의_2차가_재시도에도_실패하면_완료_저장없이_선점을_해제한다() {
        successfulModel(true);
        when(model.call(any(Prompt.class))).thenReturn(response(FeedbackTestData.analysis(FeedbackTestData.input(true))), response(null));
        assertThatThrownBy(() -> command.createCallFeedback(1L, 10L)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getBaseCode()).isEqualTo(ErrorStatus.FEEDBACK_EVALUATION_INVALID_RESPONSE));
        verify(model, times(3)).call(any(Prompt.class)); assertThat(savedCompletionStates).containsExactly(false);
        verify(repository).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L);
    }
    @Test void 만료로_교체된_선점에는_완료_결과를_저장하지_않는다() {
        successfulModel(true);
        when(repository.findByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> command.createCallFeedback(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getBaseCode()).isEqualTo(ErrorStatus.DUPLICATE_REQUEST));

        assertThat(savedCompletionStates).containsExactly(false);
        assertThat(savedCallClaim.getCompleted()).isFalse();
        assertThat(managedCallClaim.getCompleted()).isFalse();
        verify(repository).deleteByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L);
    }
    @Test void 선점_해제도_실패하면_최초_오류와_추가_실패를_함께_보존한다() {
        successfulModel(true);
        var failure = new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_API_FAILED);
        var cleanupFailure = new DataAccessResourceFailureException("cleanup unavailable");
        when(model.call(any(Prompt.class))).thenThrow(failure);
        doThrow(cleanupFailure).when(repository)
                .deleteByIdAndMemberIdAndCallIdAndCompletedFalse(CLAIM_ID, 1L, 10L);

        assertThatThrownBy(() -> command.createCallFeedback(1L, 10L))
                .isSameAs(failure)
                .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanupFailure));

        verify(model).call(any(Prompt.class));
        assertThat(savedCompletionStates).containsExactly(false);
        assertThat(managedCallClaim.getCompleted()).isFalse();
    }
    @Test void 채팅의_2차가_재시도에도_실패하면_완료_피드백을_저장하지_않는다() {
        successfulModel(false);
        when(model.call(any(Prompt.class))).thenReturn(response(FeedbackTestData.analysis(FeedbackTestData.input(false))), response(null));
        assertThatThrownBy(() -> command.createFeedback(1L, 10L)).isInstanceOf(BusinessException.class);
        verify(model, times(3)).call(any(Prompt.class)); assertThat(savedCompletionStates).isEmpty();
    }
}
