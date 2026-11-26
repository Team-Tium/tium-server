package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 저장한 1차 분석을 실제 DTO와 검증기로 재생한다. 외부 API는 호출하지 않는다. */
@EnabledIfEnvironmentVariable(named = "CONVERSATION_ANALYSIS_BATCH_DIR", matches = ".+")
class ConversationAnalysisBatchReplayTest {
    @TestFactory
    Stream<DynamicTest> 저장된_분석_결과를_검증한다() throws Exception {
        Path root = Path.of(System.getenv("CONVERSATION_ANALYSIS_BATCH_DIR"));
        List<Path> directories;
        try (var children = Files.walk(root, 2)) {
            directories = children.filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("stage1-effective.json"))).sorted().toList();
        }
        assertThat(directories).as("검사할 1차 분석 응답").isNotEmpty();
        return directories.stream().map(directory -> DynamicTest.dynamicTest(root.relativize(directory).toString(), () -> {
            var mapper = new ObjectMapper();
            var record = new LinkedHashMap<String, String>();
            record.put("semanticAccuracy", "NOT_VERIFIED");
            try (var factory = Validation.buildDefaultValidatorFactory()) {
                var input = mapper.readValue(Files.readString(directory.resolve("conversation.json")), ConversationAnalysisInput.class);
                var result = mapper.readValue(Files.readString(directory.resolve("stage1-effective.json")), ConversationAnalysisResult.class);
                new ConversationAnalysisValidator(factory.getValidator()).validateResult(input, result);
                record.put("status", "PASSED");
            } catch (Exception | AssertionError failure) {
                record.put("status", "REJECTED");
                record.put("failureType", failure.getClass().getSimpleName());
                throw failure;
            } finally {
                Files.writeString(directory.resolve("analysis-server-validation.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record));
            }
        }));
    }
}
