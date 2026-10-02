package com.studiorent.tium.domain.feed.service.query;

import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.dto.FeedSortType;

import java.util.List;

public interface FeedQueryService {

    FeedResponseDTO.FeedListDTO getFeeds(Long memberId, FeedSortType sort, String cursor);

    FeedResponseDTO.FeedListDTO getMyFeeds(Long memberId, FeedSortType sort, String cursor);

    FeedResponseDTO.FeedResultDTO getFeed(Long feedId);

    List<FeedResponseDTO.FeedHeartHistoryDTO> getMyFeedHeartHistory(Long memberId);
}
