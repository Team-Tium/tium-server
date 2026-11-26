package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import org.springframework.ai.converter.BeanOutputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;

/** DTO에서 생성한 스키마를 API 응답 형식으로 전달한다. 의미·원문 검증은 별도다. */
public final class ConversationAnalysisOutputSchema {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String JSON_SCHEMA = new BeanOutputConverter<>(ConversationAnalysisResult.class).getJsonSchema();

    private ConversationAnalysisOutputSchema() {
    }

    public static String jsonSchema() {
        return JSON_SCHEMA;
    }

    /** DTO 필드는 유지하고 이번 요청의 발화 개수와 ID만 제한한다. 순서·중복 검증은 별도다. */
    public static String forConversation(List<Utterance> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Source messages are required");
        }
        var ids = new HashSet<String>();
        for (var message : messages) {
            if (message == null || message.messageId() == null || message.messageId().isBlank()
                    || !ids.add(message.messageId())) {
                throw new IllegalArgumentException("Invalid source message IDs");
            }
        }
        var schema = MAPPER.readTree(JSON_SCHEMA);
        var properties = schema.get("properties");
        var utterances = (ObjectNode) properties.get("utterances");
        utterances.put("minItems", messages.size()).put("maxItems", messages.size());
        var values = ((ObjectNode) utterances.get("items").get("properties").get("messageId")).putArray("enum");
        messages.forEach(message -> values.add(message.messageId()));
        return MAPPER.writeValueAsString(schema);
    }
}
