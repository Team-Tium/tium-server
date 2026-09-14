package com.studiorent.tium.domain.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FeedRequestDTO {

    public record FeedDTO(
            @NotBlank(message = "content is required")
            String content
    ) {
    }

    public record FeedHeartDTO(
            @NotNull(message = "feedId is required")
            Long feedId
    ) {
    }
}
