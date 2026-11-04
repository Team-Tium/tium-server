package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 서버가 저장된 대화로 구성하는 분석 입력. messages는 실제 발화 순서로 전달한다.
 * relationship과 speaker는 LLM이 추정하지 않고 서버가 지정한다.
 */
public record ConversationAnalysisInput(
        @NotNull RelationShip relationship, // 서버가 지정한 관계
        @NotNull Speaker targetSpeaker, // 이후 피드백을 받을 사람
        @NotEmpty List<@NotNull @Valid Utterance> messages // 매세지 시간순 대화
) {
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
