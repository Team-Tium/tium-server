package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
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

/**
 * 수신자 기대값은 로컬 비교에만 사용한다. 일반 test/CI에서는 실제 API를 호출하지 않는다.
 * RECIPIENT_REGRESSION_CASES_PATH의 runs 항목은 inputPath, focusMessageId,
 * expectedRecipient, expectedAddressing을 갖는다. expectedRequester는 필요한 경우 함께 검증한다.
 * systemPromptOverridePath는 지침 비교용이며 enforceExpectation=false인 실행은 일치 여부만 기록한다.
 */
@Tag("live-api")
@EnabledIfEnvironmentVariable(named = "RECIPIENT_REGRESSION_CASES_PATH", matches = ".+")
class ConversationAnalysisRecipientLiveApiTest {
    private static final int MAX_API_CALLS = 9;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger sdkCalls = new AtomicInteger();

    @Test
    void 대리_발언과_현재_상대_반박의_수신자를_실제_분석과_비교한다() throws Exception {
        ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                org.springframework.ai.converter.BeanOutputConverter.class)).setLevel(ch.qos.logback.classic.Level.OFF);
        String manifestText = Files.readString(Path.of(System.getenv("RECIPIENT_REGRESSION_CASES_PATH")));
        var manifest = mapper.readTree(manifestText);
        int callLimit = manifest.get("maximumApiCalls").asInt();
        assertThat(manifest.get("runs").size()).isBetween(1, MAX_API_CALLS);
        assertThat(callLimit).isEqualTo(manifest.get("runs").size());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = new ConversationAnalysisValidator(factory.getValidator());
            for (var item : manifest.get("runs")) {
                validator.validateInput(readInput(item));
            }
            var live = LiveApiTestSupport.loadModel();
            assertThat(live.name()).isEqualTo(manifest.get("model").asText());
            Path output = Path.of("build/reports/recipient-attribution-regression", "run-" + Instant.now().toEpochMilli());
            Files.createDirectories(output);
            Files.writeString(output.resolve("cases-snapshot.json"), manifestText);
            var results = new ArrayList<Map<String, Object>>();
            try (var pool = Executors.newFixedThreadPool(2)) {
                var tasks = new ArrayList<java.util.concurrent.Future<Map<String, Object>>>();
                for (var item : manifest.get("runs")) {
                    tasks.add(pool.submit(() -> runCase(live, item, output, validator, callLimit)));
                }
                for (var task : tasks) { results.add(task.get()); }
            }
            save(output.resolve("summary.json"), Map.of("model", live.name(), "results", results,
                    "actualApiCalls", sdkCalls.get(), "automaticRetries", 0, "humanLabelsSentToModel", false));
            System.out.println("Recipient comparison results: " + output.toAbsolutePath());
            assertThat(sdkCalls.get()).isLessThanOrEqualTo(callLimit);
            assertThat(results.stream().allMatch(r -> "ANALYSIS_VALIDATED".equals(r.get("status"))))
                    .as("See saved per-case status in %s", output).isTrue();
            var enforced = results.stream().filter(r -> Boolean.TRUE.equals(r.get("enforceExpectation"))).toList();
            if (!enforced.isEmpty()) {
                assertThat(enforced.stream().allMatch(r -> Boolean.TRUE.equals(r.get("recipientExpectationMatched"))))
                        .as("See saved receiver classifications in %s", output).isTrue();
            } else {
                System.out.println("Classification-only comparison: expectation matches are recorded, not asserted.");
            }
        }
    }

    private Map<String, Object> runCase(LiveApiTestSupport.LiveModel live, JsonNode item,
                                       Path output, ConversationAnalysisValidator validator, int callLimit) throws Exception {
        String caseId = item.get("caseId").asText();
        Path folder = output.resolve(caseId);
        Files.createDirectories(folder);
        var input = readInput(item);
        save(folder.resolve("input.json"), input);
        var outcome = new LinkedHashMap<String, Object>();
        outcome.put("caseId", caseId);
        outcome.put("expectedRecipient", item.get("expectedRecipient").asText());
        outcome.put("expectedAddressing", item.get("expectedAddressing").asText());
        if (item.hasNonNull("expectedRequester")) {
            outcome.put("expectedRequester", item.get("expectedRequester").asText());
        }
        outcome.put("enforceExpectation", !item.has("enforceExpectation") || item.get("enforceExpectation").asBoolean());
        var entries = new AtomicInteger();
        ChatModel monitored = new ChatModel() {
            @Override public ChatOptions getDefaultOptions() { return live.model().getDefaultOptions(); }
            @Override public ChatResponse call(Prompt prompt) {
                if (entries.incrementAndGet() > 1) { throw new IllegalStateException("One API call per case"); }
                try {
                    save(folder.resolve("messages.json"), prompt.getInstructions().stream()
                            .map(m -> Map.of("role", m.getMessageType().name(), "content", m.getText())).toList());
                    if (sdkCalls.incrementAndGet() > callLimit) { throw new IllegalStateException("API budget exceeded"); }
                    var response = live.model().call(prompt);
                    String raw = response.getResult().getOutput().getText();
                    Files.writeString(folder.resolve("raw.txt"), raw == null ? "" : raw);
                    outcome.put("returnedModel", response.getMetadata().getModel());
                    return response;
                } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        };
        var builder = new ConversationAnalysisPromptBuilder(mapper);
        if (item.hasNonNull("systemPromptOverridePath")) {
            String previousSystem = Files.readString(Path.of(item.get("systemPromptOverridePath").asText()));
            builder = new ConversationAnalysisPromptBuilder(mapper) {
                @Override public Prompt build(ConversationAnalysisInput conversation) {
                    return new Prompt(List.of(new SystemMessage(previousSystem),
                            new UserMessage(mapper.writeValueAsString(conversation))));
                }
            };
        }
        long start = System.nanoTime();
        try {
            var result = new ConversationAnalysisService(monitored, builder, validator).analyze(input);
            save(folder.resolve("analysis-validated.json"), result);
            var focus = result.utterances().stream()
                    .filter(u -> u.messageId().equals(item.get("focusMessageId").asText())).findFirst().orElseThrow();
            save(folder.resolve("focus.json"), focus);
            boolean matched = !focus.requests().isEmpty() && focus.requests().stream().allMatch(r ->
                    r.recipient().name().equals(item.get("expectedRecipient").asText())
                            && r.addressing().name().equals(item.get("expectedAddressing").asText())
                            && (!item.hasNonNull("expectedRequester")
                            || r.requester().name().equals(item.get("expectedRequester").asText())));
            outcome.put("status", "ANALYSIS_VALIDATED");
            outcome.put("recipientExpectationMatched", matched);
        } catch (BusinessException failure) {
            outcome.put("status", "ANALYSIS_FAILED");
            outcome.put("error", failure.getBaseCode().toString());
        } catch (Exception | AssertionError failure) {
            outcome.put("status", "FAILED");
            outcome.put("failureType", failure.getClass().getSimpleName());
        }
        outcome.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        save(folder.resolve("outcome.json"), outcome);
        return outcome;
    }

    private ConversationAnalysisInput readInput(JsonNode item) throws Exception {
        return mapper.readValue(Files.readString(Path.of(item.get("inputPath").asText())), ConversationAnalysisInput.class);
    }

    private void save(Path path, Object value) throws java.io.IOException {
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
    }
}
