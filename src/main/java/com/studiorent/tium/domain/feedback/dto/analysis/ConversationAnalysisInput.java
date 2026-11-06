package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * 서버가 저장된 대화로 구성하는 분석 입력. messages는 실제 발화 순서로 전달한다.
 * relationship과 speaker는 LLM이 추정하지 않고 서버가 지정한다.
 */
public record ConversationAnalysisInput(
        @NotNull RelationShip relationship, // 서버가 지정한 관계
        @NotNull Speaker targetSpeaker, // 이후 피드백을 받을 사람
        @NotEmpty List<@NotNull @Valid Utterance> messages, // 메시지 시간순 대화
        @Nullable @Valid Scope scope // 통화 등 일부 구간만 분석하는 경우 서버가 명시한다.
) {
    public ConversationAnalysisInput(RelationShip relationship, Speaker targetSpeaker, List<Utterance> messages) {
        this(relationship, targetSpeaker, messages, null);
    }

    public record Scope(
            @Positive int maxUtterances,
            @NotBlank String description
    ) {
    }

    public enum Speaker {
        ME,
        OTHER
    }

    public record Utterance(
            @NotBlank String messageId,
            @NotNull Speaker speaker,
            @NotBlank String text
    ) {
    }
}
