package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationFeedbackEvaluationOutputSchemaTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Utterance> messages = List.of(new Utterance("a", Speaker.ME, "내 말"),
            new Utterance("b", Speaker.OTHER, "상대 말"));

    private List<JsonNode> propertiesNamed(JsonNode node, String name) {
        List<JsonNode> found = new ArrayList<>();
        if (node.isObject()) {
            if (node.has("properties") && node.get("properties").has(name)) {
                found.add(node.get("properties").get(name));
            }
            node.properties().forEach(entry -> found.addAll(propertiesNamed(entry.getValue(), name)));
        } else if (node.isArray()) {
            node.forEach(child -> found.addAll(propertiesNamed(child, name)));
        }
        return found;
    }

    @Test
    void 평가_대상과_상대의_ID_범위를_분리하고_화자를_뒤집어도_적용한다() {
        for (var target : Speaker.values()) {
            var schema = mapper.readTree(ConversationFeedbackEvaluationOutputSchema.forConversation(messages, target));
            String own = target == Speaker.ME ? "a" : "b";
            String other = target == Speaker.ME ? "b" : "a";
            assertThat(propertiesNamed(schema, "targetMessageIds")).isNotEmpty().allSatisfy(node ->
                    assertThat(node.get("items").get("enum").toString()).isEqualTo("[\"" + own + "\"]"));
            assertThat(propertiesNamed(schema, "respondingToMessageIds")).isNotEmpty().allSatisfy(node ->
                    assertThat(node.get("items").get("enum").toString()).isEqualTo("[\"" + other + "\"]"));
            var choices = schema.get("$defs").get("Evidence").get("anyOf");
            assertThat(choices.size()).isEqualTo(2);
            for (int i = 0; i < messages.size(); i++) {
                var properties = choices.get(i).get("properties");
                assertThat(properties.get("messageId").get("enum").get(0).asText()).isEqualTo(messages.get(i).messageId());
                assertThat(properties.get("quote").get("enum").get(0).asText()).isEqualTo(messages.get(i).text());
            }
            assertThat(schema.get("properties").get("targetSpeaker").get("enum").get(0).asText()).isEqualTo(target.name());
        }
    }

    @Test
    void 상대_발화가_없으면_응답_대상_ID는_빈_배열만_허용한다() {
        var schema = mapper.readTree(ConversationFeedbackEvaluationOutputSchema.forConversation(messages.subList(0, 1), Speaker.ME));
        assertThat(propertiesNamed(schema, "respondingToMessageIds")).isNotEmpty().allSatisfy(node -> {
            assertThat(node.get("maxItems").asInt()).isZero();
            assertThat(node.get("items").has("enum")).isFalse();
        });
    }

    @Test
    void 요청별_바인딩은_기본_스키마를_변경하지_않는다() {
        var before = ConversationFeedbackEvaluationOutputSchema.baseJsonSchema();
        ConversationFeedbackEvaluationOutputSchema.forConversation(messages, Speaker.ME);
        ConversationFeedbackEvaluationOutputSchema.forConversation(messages, Speaker.OTHER);
        assertThat(ConversationFeedbackEvaluationOutputSchema.baseJsonSchema()).isEqualTo(before);
    }

    @Test
    void 중복_ID와_대상_화자_부재를_거절한다() {
        assertThatThrownBy(() -> ConversationFeedbackEvaluationOutputSchema.forConversation(
                List.of(messages.getFirst(), messages.getFirst()), Speaker.ME)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConversationFeedbackEvaluationOutputSchema.forConversation(
                messages.subList(0, 1), Speaker.OTHER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 기본_스키마의_모든_객체는_필수_필드와_추가_필드_금지를_가진다() {
        verifyStrict(mapper.readTree(ConversationFeedbackEvaluationOutputSchema.baseJsonSchema()));
    }

    @Test
    void 내보낸_기본_스키마가_DTO와_일치한다() throws Exception {
        try (var resource = getClass().getResourceAsStream("/ai/conversation-evaluation.schema.json")) {
            assertThat(resource).isNotNull();
            assertThat(mapper.readTree(new String(resource.readAllBytes(), StandardCharsets.UTF_8)))
                    .isEqualTo(mapper.readTree(ConversationFeedbackEvaluationOutputSchema.baseJsonSchema()));
        }
    }

    private void verifyStrict(JsonNode node) {
        if (node.isObject()) {
            if (node.has("properties")) {
                assertThat(node.get("additionalProperties").asBoolean()).isFalse();
                var actual = new HashSet<String>();
                node.get("required").forEach(x -> actual.add(x.asText()));
                var expected = new HashSet<String>();
                node.get("properties").properties().forEach(x -> expected.add(x.getKey()));
                assertThat(actual).isEqualTo(expected);
            }
            node.properties().forEach(x -> verifyStrict(x.getValue()));
        } else if (node.isArray()) {
            node.forEach(this::verifyStrict);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "EVALUATION_SCHEMA_EXPORT_PATH", matches = ".+")
    void 기본_스키마를_실험용으로_내보낸다() throws Exception {
        Path path = Path.of(System.getenv("EVALUATION_SCHEMA_EXPORT_PATH"));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, ConversationFeedbackEvaluationOutputSchema.baseJsonSchema());
    }
}
