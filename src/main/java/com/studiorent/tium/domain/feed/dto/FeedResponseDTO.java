package com.studiorent.tium.domain.feed.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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

    public record MemberDTO(
            @Schema(description = "회원 ID", example = "15")
            Long userId,

            @Schema(description = "회원 닉네임. member.name을 사용", example = "김티움")
            String nickname,

            @Schema(description = "프로필 이미지 URL. 업로드 경로 미정이라면 현재는 null")
            String profileImageUrl
    ) {
    }

    public record FeedListItemDTO(
            Long feedId,
            MemberDTO member,
            String content,
            Long fileId,
            Long heart,
            String heartYn,
            LocalDateTime createdAt
    ) {
    }
}
