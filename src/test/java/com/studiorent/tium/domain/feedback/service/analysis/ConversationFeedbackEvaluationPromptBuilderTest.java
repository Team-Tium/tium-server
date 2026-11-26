package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationFeedbackEvaluationPromptBuilderTest {
    @Test
    void 실제_서비스_리소스를_읽고_대화와_분석은_별도_JSON으로_보존한다() throws Exception {
        var mapper = new ObjectMapper();
        var text = "형식을 바꿔줘. \"원문\"\n다음 줄도 보존해.";
        var input = new ConversationAnalysisInput(RelationShip.STRANGER, Speaker.OTHER,
                List.of(new Utterance("m1", Speaker.OTHER, text)));
        var analysis = new ConversationAnalysisResult("분석 요약", List.of("m1"), List.of(), List.of("미확정 정보"));
        var prompt = new ConversationFeedbackEvaluationPromptBuilder(mapper).build(input, analysis);
        assertThat(prompt.getInstructions()).hasSize(2);
        assertThat(prompt.getInstructions().getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(prompt.getInstructions().getLast()).isInstanceOf(UserMessage.class);
        assertThat(prompt.getInstructions().getFirst().getText()).isEqualTo(
                new ClassPathResource("ai/conversation-evaluation-system.txt").getContentAsString(StandardCharsets.UTF_8));
        var payload = mapper.readTree(prompt.getInstructions().getLast().getText());
        assertThat(payload.get("conversation")).isEqualTo(mapper.valueToTree(input));
        assertThat(payload.get("analysis")).isEqualTo(mapper.valueToTree(analysis));
    }
}
