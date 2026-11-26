package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 외부 API를 호출하지 않고 배치에서 저장한 각 2차 응답을 실제 서버 검증기로 검사한다. */
@EnabledIfEnvironmentVariable(named = "FEEDBACK_EVALUATION_BATCH_DIR", matches = ".+")
class ConversationFeedbackEvaluationBatchReplayTest {
    @TestFactory
    Stream<DynamicTest> 저장된_배치_응답을_각각_검증한다() throws Exception {
        Path root = Path.of(System.getenv("FEEDBACK_EVALUATION_BATCH_DIR"));
        List<Path> directories;
        try (var children = Files.walk(root, 2)) {
            directories = children.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("stage2-parsed.json"))).sorted().toList();
        }
        assertThat(directories).as("2차 응답이 존재하는 사례").isNotEmpty();
        return directories.stream().map(directory -> DynamicTest.dynamicTest(root.relativize(directory).toString(), () -> {
            var mapper = new ObjectMapper();
            var record = new LinkedHashMap<String, String>();
            record.put("gradeAuthority", "SECOND_STAGE_AI");
            record.put("validator", "ConversationFeedbackEvaluationValidator");
            record.put("semanticAccuracy", "NOT_VERIFIED");
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var conversation = mapper.readTree(Files.readString(directory.resolve("conversation.json")));
                var source = new ArrayList<Utterance>();
                for (var item : conversation.get("messages")) {
                    source.add(new Utterance(item.get("messageId").asText(), Speaker.valueOf(item.get("speaker").asText()), item.get("text").asText()));
                }
                var evaluation = mapper.readValue(Files.readString(directory.resolve("stage2-parsed.json")), ConversationFeedbackEvaluation.class);
                record.put("overallQuality", evaluation.overallQuality() == null ? "null" : evaluation.overallQuality().name());
                new ConversationFeedbackEvaluationValidator(factory.getValidator())
                        .validateResult(source, Speaker.valueOf(conversation.get("targetSpeaker").asText()), evaluation);
                record.put("status", "PASSED");
            } catch (Exception | AssertionError failure) {
                record.put("status", "REJECTED");
                record.put("failureType", failure.getClass().getSimpleName());
                throw failure;
            } finally {
                Files.writeString(directory.resolve("server-validation.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record));
            }
        }));
    }
}
