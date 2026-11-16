package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;

/** 평가 DTO 스키마에 서버가 확인한 화자별 ID 범위를 적용한다. 등급·판단 이유는 제한하지 않는다. */
public final class ConversationFeedbackEvaluationOutputSchema {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE = new BeanOutputConverter<>(ConversationFeedbackEvaluation.class).getJsonSchema();

    private ConversationFeedbackEvaluationOutputSchema() {
    }

    public static String baseJsonSchema() {
        return BASE;
    }

    public static String forConversation(List<Utterance> messages, Speaker target) {
        if (messages == null || messages.isEmpty() || target == null) {
            throw new IllegalArgumentException("Source and target are required");
        }
        var seen = new HashSet<String>();
        for (var message : messages) {
            if (message == null || message.text() == null || message.text().isBlank()
                    || message.messageId() == null || message.messageId().isBlank()
                    || message.speaker() == null || !seen.add(message.messageId())) {
                throw new IllegalArgumentException("Invalid source message IDs or speakers");
            }
        }
        var targetIds = messages.stream().filter(m -> m.speaker() == target).map(Utterance::messageId).toList();
        var otherIds = messages.stream().filter(m -> m.speaker() != target).map(Utterance::messageId).toList();
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("Target speaker is absent");
        }
        JsonNode schema = MAPPER.readTree(BASE);
        bind(schema, target, targetIds, otherIds, messages.stream().map(Utterance::messageId).toList());
        // 인용을 자유롭게 재작성하지 않고 ID와 원문 전체의 정확한 쌍에서 고른다.
        var evidence = (ObjectNode) schema.get("$defs").get("Evidence");
        evidence.removeAll();
        var choices = evidence.putArray("anyOf");
        for (var message : messages) {
            var choice = choices.addObject();
            choice.put("type", "object");
            var properties = choice.putObject("properties");
            properties.putObject("messageId").put("type", "string").putArray("enum").add(message.messageId());
            properties.putObject("quote").put("type", "string").putArray("enum").add(message.text());
            choice.putArray("required").add("messageId").add("quote");
            choice.put("additionalProperties", false);
        }
        return MAPPER.writeValueAsString(schema);
    }

    private static void bind(JsonNode node, Speaker target, List<String> targetIds, List<String> otherIds, List<String> allIds) {
        if (node.isObject()) {
            if (node.has("properties")) {
                var properties = node.get("properties");
                if (properties.has("targetMessageIds")) {
                    bindArray((ObjectNode) properties.get("targetMessageIds"), targetIds);
                }
                if (properties.has("respondingToMessageIds")) {
                    bindArray((ObjectNode) properties.get("respondingToMessageIds"), otherIds);
                }
                if (properties.has("messageId")) {
                    var values = ((ObjectNode) properties.get("messageId")).putArray("enum");
                    allIds.forEach(values::add);
                }
                if (properties.has("targetSpeaker")) {
                    ((ObjectNode) properties.get("targetSpeaker")).putArray("enum").add(target.name());
                }
            }
            node.properties().forEach(entry -> bind(entry.getValue(), target, targetIds, otherIds, allIds));
        } else if (node.isArray()) {
            node.forEach(child -> bind(child, target, targetIds, otherIds, allIds));
        }
    }

    private static void bindArray(ObjectNode array, List<String> ids) {
        if (ids.isEmpty()) {
            array.put("maxItems", 0); // 상대 발화가 없으면 빈 배열만 허용하며 가상의 ID를 만들지 않는다.
        } else {
            var values = ((ObjectNode) array.get("items")).putArray("enum");
            ids.forEach(values::add);
        }
    }
}
