package com.studiorent.tium.domain.feed.dto;

import java.time.LocalDateTime;

public class FeedResponseDTO {

    public record FeedResultDTO(
            Long feedId,
            String content,
            Long heart
    ) {
    }

    public record FeedHeartResultDTO(
            Long feedId,
            Long heart,
            String heartYn
    ) {
    }

    public record FeedHeartHistoryDTO(
            Long feedId,
            Long memberId,
            LocalDateTime heartedAt
    ) {
    }
}
