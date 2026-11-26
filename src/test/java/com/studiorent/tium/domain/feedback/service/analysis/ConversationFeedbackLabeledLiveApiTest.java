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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

/** 명시한 라벨링 파일의 10건을 실제 API 서비스와 동일한 Java 파이프라인으로 실행한다. */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "LABELED_FEEDBACK_CASES_PATH", matches = ".+")
class ConversationFeedbackLabeledLiveApiTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 라벨링_10건의_두_단계_응답과_실패를_모두_기록한다() throws Exception {
        Path labelsPath = Path.of(System.getenv("LABELED_FEEDBACK_CASES_PATH"));
        String labelsText = Files.readString(labelsPath);
        JsonNode labels = mapper.readTree(labelsText);
        assertThat(labels.get("cases").size()).isEqualTo(10);
        assertThat(sha256(Path.of(labels.get("workbookPath").asText())))
                .isEqualTo(labels.get("workbookSha256").asText());
        var live = LiveApiTestSupport.loadModel();
        Path output = Path.of("build/reports/feedback-service-labeled", "run-" + Instant.now().toEpochMilli());
        Files.createDirectories(output);
        Files.writeString(output.resolve("labels-snapshot.json"), labelsText);
        Files.writeString(output.resolve("evaluation-system.txt"), Files.readString(
                Path.of("src/main/resources/ai/conversation-evaluation-system.txt")));
        List<Map<String, Object>> results = new ArrayList<>();
        long start = System.nanoTime();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Map<String, Object>>>();
            for (var item : labels.get("cases")) {
                tasks.add(pool.submit(() -> runCase(live, item, output)));
            }
            for (var task : tasks) { results.add(task.get()); }
        }
        long passed = results.stream().filter(item -> "PASSED".equals(item.get("status"))).count();
        var summary = new LinkedHashMap<String, Object>();
        summary.put("model", live.name()); summary.put("cases", results);
        summary.put("passed", passed); summary.put("failed", results.size() - passed);
        summary.put("apiCalls", results.stream().mapToInt(item -> (Integer) item.get("apiCalls")).sum());
        summary.put("matched", results.stream().filter(item -> Boolean.TRUE.equals(item.get("gradeMatch"))).count());
        summary.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        summary.put("scope", "REAL_JAVA_PIPELINE_WITH_AI_HUB_INPUT_NO_DATABASE_WRITE");
        summary.put("automaticRetries", 0); summary.put("humanLabelsProvidedToModel", false);
        save(output.resolve("summary.json"), summary);
        assertThat(passed).as("전체 사례의 단계별 결과는 %s에 보존", output).isEqualTo(10);
    }

    private Map<String, Object> runCase(LiveApiTestSupport.LiveModel live, JsonNode item, Path output) throws Exception {
        String id = item.get("dialogueId").asText();
        Path folder = output.resolve(id); Files.createDirectories(folder);
        var record = new LinkedHashMap<String, Object>();
        record.put("dialogueId", id); record.put("expectedGrade", item.get("expectedGrade").asText());
        var calls = new AtomicInteger();
        ChatModel monitored = new ChatModel() {
            @Override public ChatOptions getDefaultOptions() { return live.model().getDefaultOptions(); }
            @Override public ChatResponse call(Prompt prompt) {
                int number = calls.incrementAndGet();
                try {
                    // 모델 옵션에는 키가 있을 수 있으므로 기록하지 않는다.
                    save(folder.resolve("stage" + number + "-messages.json"), prompt.getInstructions().stream()
                            .map(message -> Map.of("role", message.getMessageType().name(), "content", message.getText())).toList());
                    var response = live.model().call(prompt);
                    Files.writeString(folder.resolve("stage" + number + "-raw.txt"), response.getResult().getOutput().getText());
                    return response;
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            }
        };
        long start = System.nanoTime();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Path source = Path.of(item.get("sourcePath").asText());
            assertThat(sha256(source)).isEqualTo(item.get("sourceSha256").asText());
            var original = mapper.readTree(Files.readString(source));
            assertThat(original.get("header").get("dialogueInfo").get("dialogueID").asText()).isEqualTo(id);
            List<Utterance> messages = new ArrayList<>();
            for (var message : original.get("body")) {
                messages.add(new Utterance(message.get("utteranceID").asText(),
                        item.get("targetParticipant").asText().equals(message.get("participantID").asText())
                                ? Speaker.ME : Speaker.OTHER, message.get("utterance").asText()));
            }
            var input = new ConversationAnalysisInput(RelationShip.UNCONFIRMED, Speaker.ME, messages);
            save(folder.resolve("input.json"), input);
            var validator = new ConversationAnalysisValidator(factory.getValidator());
            var service = new ConversationFeedbackService(
                    new ConversationAnalysisService(monitored, new ConversationAnalysisPromptBuilder(mapper), validator),
                    new ConversationFeedbackEvaluationService(monitored, new ConversationFeedbackEvaluationPromptBuilder(mapper),
                            validator, new ConversationFeedbackEvaluationValidator(factory.getValidator())));
            var response = service.generate(input);
            save(folder.resolve("response.json"), response);
            record.put("status", "PASSED"); record.put("actualGrade", response.overallQuality());
            record.put("gradeMatch", response.overallQuality().equals(item.get("expectedGrade").asText()));
            record.put("feedback", response.overallFeedback());
        } catch (BusinessException failure) {
            record.put("status", calls.get() < 2 ? "ANALYSIS_FAILED" : "EVALUATION_FAILED");
            record.put("error", failure.getBaseCode().toString());
        } catch (Exception | AssertionError failure) {
            record.put("status", "FAILED"); record.put("failureType", failure.getClass().getSimpleName());
        }
        record.put("apiCalls", calls.get()); record.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        save(folder.resolve("outcome.json"), record);
        return record;
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private void save(Path path, Object value) throws java.io.IOException {
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
