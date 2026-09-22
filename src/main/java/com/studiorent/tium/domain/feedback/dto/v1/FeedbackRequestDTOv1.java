package com.studiorent.tium.domain.feedback.dto.v1;

import com.studiorent.tium.domain.feedback.dto.ConversationTurn;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import javax.annotation.Nullable;
import java.util.List;

public record FeedbackRequestDTOv1(
        @NotBlank  String conversationId,  // 대화 아이디
        @NotBlank String userId,   // 유저 입장이 필요할것같아서
        @Nullable RelationShip relationship, // 관계
        @Nullable String goal, // 목표  말잘하게하기?
        @NotEmpty List<@Valid ConversationTurn> conversation
)
{}
