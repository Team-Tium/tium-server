package com.studiorent.tium.domain.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

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

    public record FeedListDTO(
            List<FeedListItemDTO> feeds,
            Boolean hasNext,
            String nextCursor
    ) {
    }

    public record FeedListItemDTO(
            Long feedId,
            Long memberId,
            String content,
            Long fileId,
            Long heart,
            String heartYn,
            LocalDateTime createdAt
    ) {
    }
}
