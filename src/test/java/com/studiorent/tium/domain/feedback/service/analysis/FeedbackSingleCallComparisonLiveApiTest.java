package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.global.exception.BusinessException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** 같은 모델·원문의 기본 단일 요청과 실제 Java 2단계 서비스 비교. 평가 라벨은 모델에 보내지 않는다. */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "FEEDBACK_COMPARISON_INPUT_PATH", matches = ".+")
class FeedbackSingleCallComparisonLiveApiTest {
    private static final String BASIC_REQUEST = "아래 대화를 읽고 ME의 대화에 대한 피드백을 해 줘. "
            + "ME는 피드백을 받을 사용자이고 OTHER는 대화 상대야.";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 같은_10건의_기본_요청과_서비스_결과를_재작성없이_비교한다() throws Exception {
        Path prepared = Path.of(System.getenv("FEEDBACK_COMPARISON_INPUT_PATH"));
        String casesText = Files.readString(prepared);
        var cases = mapper.readTree(casesText).get("cases");
        assertThat(cases.size()).isEqualTo(10);
        var live = LiveApiTestSupport.loadModel();
        Path run = Path.of("build/reports/feedback-comparison", "run-" + Instant.now().toEpochMilli());
        Files.createDirectories(run);
        Files.writeString(run.resolve("inputs-snapshot.json"), casesText);
        Files.writeString(run.resolve("basic-request.txt"), BASIC_REQUEST);
        Files.writeString(run.resolve("evaluation-system.txt"), Files.readString(
                Path.of("src/main/resources/ai/conversation-evaluation-system.txt")));
        save(run.resolve("run.json"), Map.of("model", live.name(), "cases", 10, "maximumApiCalls", 30,
                "automaticRetries", 0, "baseline", "BASIC_REQUEST_WITHOUT_RUBRIC", "humanLabelsSent", false,
                "scope", "MODEL_API_COMPARISON_NOT_CHATGPT_PRODUCT_OR_STAGE_ONLY_ABLATION"));
        long start = System.nanoTime();
        List<Map<String, Object>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<Map<String, Object>>>();
            int index = 0;
            for (var item : cases) {
                // 어느 방식을 항상 먼저 호출하는지에 따른 영향을 줄인다.
                for (var arm : index++ % 2 == 0 ? List.of("single", "service") : List.of("service", "single")) {
                    futures.add(pool.submit(() -> runArm(live, item, arm, run)));
                }
            }
            for (var future : futures) { results.add(future.get()); }
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("model", live.name()); summary.put("results", results);
        summary.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        summary.put("apiCalls", results.stream().mapToInt(item -> (Integer) item.get("apiCalls")).sum());
        save(run.resolve("summary.json"), summary);
        assertThat(results).hasSize(20);
        assertThat((Integer) summary.get("apiCalls")).isLessThanOrEqualTo(30);
        // 모델의 거절·검증 실패도 비교 대상이다. 성공한 것만 남기거나 재호출하지 않는다.
    }

    private Map<String, Object> runArm(LiveApiTestSupport.LiveModel live, JsonNode item, String arm, Path run) throws Exception {
        String caseId = item.get("caseId").asText();
        Path folder = run.resolve(caseId).resolve(arm); Files.createDirectories(folder);
        var calls = new AtomicInteger(); var promptTokens = new AtomicInteger(); var completionTokens = new AtomicInteger();
        ChatModel monitored = new ChatModel() {
            @Override public ChatOptions getDefaultOptions() { return live.model().getDefaultOptions(); }
            @Override public ChatResponse call(Prompt prompt) {
                int number = calls.incrementAndGet();
                try {
                    // ChatOptions에는 키가 있을 수 있으므로 모델 옵션·헤더는 기록하지 않는다.
                    save(folder.resolve("call-" + number + "-messages.json"), prompt.getInstructions().stream()
                            .map(message -> Map.of("role", message.getMessageType().name(), "content", message.getText())).toList());
                    var response = live.model().call(prompt);
                    String text = response.getResult().getOutput().getText();
                    Files.writeString(folder.resolve("call-" + number + "-raw.txt"), text == null ? "" : text);
                    var usage = response.getMetadata().getUsage();
                    if (usage != null) {
                        if (usage.getPromptTokens() != null) { promptTokens.addAndGet(usage.getPromptTokens()); }
                        if (usage.getCompletionTokens() != null) { completionTokens.addAndGet(usage.getCompletionTokens()); }
                    }
                    return response;
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        };
        var result = new LinkedHashMap<String, Object>();
        result.put("caseId", caseId); result.put("arm", arm); result.put("dialogueId", item.get("dialogueId").asText());
        long start = System.nanoTime();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var conversation = item.get("conversation");
            if (arm.equals("single")) {
                var response = monitored.call(new Prompt(List.of(
                        new SystemMessage("당신은 사용자의 요청에 답하는 AI 어시스턴트입니다."),
                        new UserMessage(BASIC_REQUEST + "\n\n" + mapper.writeValueAsString(conversation)))));
                String finish = response.getResult().getMetadata().getFinishReason();
                String feedback = response.getResult().getOutput().getText();
                if (feedback == null || feedback.isBlank() || (finish != null && !finish.isBlank() && !finish.equalsIgnoreCase("stop"))) {
                    result.put("status", "INCOMPLETE_OR_REFUSED");
                } else {
                    result.put("status", "RETURNED"); result.put("feedback", feedback);
                }
            } else {
                List<Utterance> messages = new ArrayList<>();
                for (var message : conversation.get("messages")) {
                    messages.add(new Utterance(message.get("messageId").asText(),
                            Speaker.valueOf(message.get("speaker").asText()), message.get("text").asText()));
                }
                var input = new ConversationAnalysisInput(RelationShip.UNCONFIRMED, Speaker.ME, messages);
                var validator = new ConversationAnalysisValidator(factory.getValidator());
                var pipeline = new ConversationFeedbackService(
                        new ConversationAnalysisService(monitored, new ConversationAnalysisPromptBuilder(mapper), validator),
                        new ConversationFeedbackEvaluationService(monitored, new ConversationFeedbackEvaluationPromptBuilder(mapper),
                                validator, new ConversationFeedbackEvaluationValidator(factory.getValidator())));
                var response = pipeline.generate(input);
                save(folder.resolve("response.json"), response);
                result.put("status", "VALIDATED"); result.put("actualGrade", response.overallQuality());
                result.put("feedback", response.overallFeedback());
            }
        } catch (BusinessException failure) {
            result.put("status", calls.get() < 2 ? "ANALYSIS_REJECTED" : "EVALUATION_REJECTED");
            result.put("error", failure.getBaseCode().toString());
        } catch (Exception failure) {
            result.put("status", "FAILED"); result.put("failureType", failure.getClass().getSimpleName());
        }
        result.put("apiCalls", calls.get()); result.put("inputTokens", promptTokens.get());
        result.put("outputTokens", completionTokens.get()); result.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        save(folder.resolve("outcome.json"), result);
        return result;
    }

    private void save(Path path, Object value) throws java.io.IOException {
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
