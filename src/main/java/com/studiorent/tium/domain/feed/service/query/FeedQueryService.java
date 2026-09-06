package com.studiorent.tium.domain.feed.service.query;

import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;

import java.util.List;

public interface FeedQueryService {

    FeedResponseDTO.FeedResultDTO getFeed(Long feedId);

    List<FeedResponseDTO.FeedHeartHistoryDTO> getMyFeedHeartHistory(Long memberId);
}
