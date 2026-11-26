package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** 저장된 실제 응답만 검증한다. 외부 API를 호출하지 않으며 일반 실행에서는 건너뛴다. */
@EnabledIfEnvironmentVariable(named = "FEEDBACK_EVALUATION_REPLAY_DIR", matches = ".+")
class ConversationFeedbackEvaluationReplayTest {
    @Test
    void 실제_평가_JSON을_서버_DTO로_읽고_검증한다() throws Exception {
        var directory = Path.of(System.getenv("FEEDBACK_EVALUATION_REPLAY_DIR"));
        var mapper = new ObjectMapper();
        var conversation = mapper.readTree(Files.readString(directory.resolve("conversation.json")));
        var source = new ArrayList<Utterance>();
        for (var item : conversation.get("messages")) {
            source.add(new Utterance(item.get("messageId").asText(), Speaker.valueOf(item.get("speaker").asText()), item.get("text").asText()));
        }
        var result = mapper.readValue(Files.readString(directory.resolve("stage2-parsed.json")), ConversationFeedbackEvaluation.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            new ConversationFeedbackEvaluationValidator(factory.getValidator())
                    .validateResult(source, Speaker.valueOf(conversation.get("targetSpeaker").asText()), result);
        }
        System.out.printf("Validated AI grade: %s, reason: %s%n", result.overallQuality(), result.gradeReason());
    }
}
