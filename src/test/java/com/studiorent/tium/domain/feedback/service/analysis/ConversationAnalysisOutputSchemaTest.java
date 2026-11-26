package com.studiorent.tium.domain.feedback.service.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAnalysisOutputSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 모든_객체의_필드를_필수로_하고_추가_필드를_금지한다() {
        var schema = mapper.readTree(ConversationAnalysisOutputSchema.jsonSchema());
        assertThat(schema.get("type").asText()).isEqualTo("object");
        verifyStrictObjects(schema);
        assertThat(ConversationAnalysisOutputSchema.jsonSchema()).contains("requests", "uncertainties", "THIRD_PARTY", "UNCERTAIN");
    }

    @Test
    void 요청의_발화_개수와_ID를_제한하고_기본_스키마는_변경하지_않는다() {
        var base = ConversationAnalysisOutputSchema.jsonSchema();
        var messages = List.of(new Utterance("u1", Speaker.ME, "첫 말"), new Utterance("u2", Speaker.OTHER, "둘째 말"));
        var schema = mapper.readTree(ConversationAnalysisOutputSchema.forConversation(messages));
        var array = schema.get("properties").get("utterances");
        assertThat(array.get("minItems").asInt()).isEqualTo(2);
        assertThat(array.get("maxItems").asInt()).isEqualTo(2);
        assertThat(array.get("items").get("properties").get("messageId").get("enum").toString()).isEqualTo("[\"u1\",\"u2\"]");
        assertThat(ConversationAnalysisOutputSchema.jsonSchema()).isEqualTo(base);
        assertThatThrownBy(() -> ConversationAnalysisOutputSchema.forConversation(List.of(messages.getFirst(), messages.getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConversationAnalysisOutputSchema.forConversation(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void verifyStrictObjects(JsonNode node) {
        if (node.isObject()) {
            if (node.has("properties")) {
                assertThat(node.get("additionalProperties").asBoolean()).isFalse();
                var names = new HashSet<String>();
                node.get("properties").properties().forEach(entry -> names.add(entry.getKey()));
                var required = new HashSet<String>();
                node.get("required").forEach(value -> required.add(value.asText()));
                assertThat(required).isEqualTo(names);
            }
            node.properties().forEach(entry -> verifyStrictObjects(entry.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::verifyStrictObjects);
        }
    }

    @Test
    void 실험용_스키마가_현재_DTO에서_생성한_스키마와_일치한다() throws Exception {
        try (var resource = getClass().getResourceAsStream("/ai/conversation-analysis.schema.json")) {
            assertThat(resource).isNotNull();
            var exported = mapper.readTree(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exported).isEqualTo(mapper.readTree(ConversationAnalysisOutputSchema.jsonSchema()));
        }
    }

    /** 명시적인 내보내기 때만 실험 코드가 읽을 스키마 파일을 갱신한다. */
    @Test
    @EnabledIfEnvironmentVariable(named = "ANALYSIS_SCHEMA_EXPORT_PATH", matches = ".+")
    void schema를_실험용_파일로_내보낸다() throws Exception {
        Path path = Path.of(System.getenv("ANALYSIS_SCHEMA_EXPORT_PATH"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, ConversationAnalysisOutputSchema.jsonSchema());
    }
}
