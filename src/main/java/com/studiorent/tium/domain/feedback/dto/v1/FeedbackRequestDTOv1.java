package com.studiorent.tium.domain.feedback.dto.v1;

import com.studiorent.tium.domain.feedback.dto.ConversationTurn;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record FeedbackRequestDTOv1(
        @Nullable RelationShip relationship, // 관계
        @Nullable String goal, // 목표  말잘하게하기?
        @NotEmpty List<@Valid ConversationTurn> conversation
)
{}
