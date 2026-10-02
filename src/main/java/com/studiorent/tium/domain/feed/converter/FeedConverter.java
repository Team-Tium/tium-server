package com.studiorent.tium.domain.feed.converter;

import com.studiorent.tium.domain.feed.dto.FeedRequestDTO;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.entity.FeedLikeLog;
import com.studiorent.tium.domain.feed.repository.projection.FeedListProjection;

public class FeedConverter {

    public static Feed toFeed(Long memberId, FeedRequestDTO.FeedDTO request) {
        return Feed.builder()
                .memberId(memberId)
                .content(request.content())
                .heart(0L)
                .deleteYn("N")
                .build();
    }

    public static FeedResponseDTO.FeedResultDTO toFeedResult(Feed feed) {
        return new FeedResponseDTO.FeedResultDTO(
                feed.getId(),
                feed.getContent(),
                feed.getHeart()
        );
    }

    public static FeedResponseDTO.FeedListItemDTO toFeedListItem(FeedListProjection feed, boolean hearted) {
        return new FeedResponseDTO.FeedListItemDTO(
                feed.getFeedId(),
                new FeedResponseDTO.MemberDTO(
                        feed.getMemberId(),
                        feed.getNickname(),
                        feed.getProfileImageUrl()
                ),
                feed.getContent(),
                feed.getFileId(),
                feed.getHeart(),
                hearted ? "Y" : "N",
                feed.getCreatedAt()
        );
    }

    public static FeedResponseDTO.FeedHeartResultDTO toFeedHeartResult(Feed feed, FeedLikeLog feedLikeLog) {
        return new FeedResponseDTO.FeedHeartResultDTO(
                feed.getId(),
                feed.getHeart(),
                feedLikeLog.getHeartYn()
        );
    }

    public static FeedResponseDTO.FeedHeartHistoryDTO toFeedHeartHistory(FeedLikeLog feedLikeLog) {
        return new FeedResponseDTO.FeedHeartHistoryDTO(
                feedLikeLog.getFeed().getId(),
                feedLikeLog.getMemberId(),
                feedLikeLog.getHeartedAt()
        );
    }
}
