package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import jakarta.validation.Validation;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** 관계 미확인인 로컬 실험도 실제 분석 DTO의 필수 필드를 검사한다. 의미·원문 참조 검증은 별도다. */
@EnabledIfEnvironmentVariable(named = "CONVERSATION_ANALYSIS_CONTRACT_DIR", matches = ".+")
class ConversationAnalysisContractReplayTest {
    @TestFactory
    Stream<DynamicTest> 저장된_분석의_DTO_계약을_검증한다() throws Exception {
        Path root = Path.of(System.getenv("CONVERSATION_ANALYSIS_CONTRACT_DIR"));
        try (var paths = Files.walk(root, 3)) {
            var files = paths.filter(path -> path.getFileName().toString().equals("stage1-parsed.json")).sorted().toList();
            assertThat(files).isNotEmpty();
            return files.stream().map(file -> DynamicTest.dynamicTest(root.relativize(file).toString(), () -> {
                var mapper = new ObjectMapper();
                var record = new LinkedHashMap<String, String>();
                record.put("scope", "DTO_CONTRACT_ONLY");
                try (var factory = Validation.buildDefaultValidatorFactory()) {
                    Path raw = file.getParent().resolve("stage1-parsed.json");
                    Path inspected = Files.exists(raw) ? raw : file;
                    record.put("inputFile", inspected.getFileName().toString());
                    var result = mapper.readValue(Files.readString(inspected), ConversationAnalysisResult.class);
                    var violations = factory.getValidator().validate(result);
                    record.put("violationPaths", violations.stream().map(v -> v.getPropertyPath().toString())
                            .sorted().collect(Collectors.joining(",")));
                    assertThat(violations).isEmpty();
                    record.put("status", "PASSED");
                } catch (Exception | AssertionError failure) {
                    record.put("status", "REJECTED");
                    record.put("failureType", failure.getClass().getSimpleName());
                    throw failure;
                } finally {
                    Files.writeString(file.getParent().resolve("analysis-contract-validation.json"),
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record));
                }
            }));
        }
    }
}
