package com.studiorent.tium.domain.feed.service.command;

import com.studiorent.tium.domain.feed.dto.FeedRequestDTO;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;

public interface FeedCommandService {

    FeedResponseDTO.FeedResultDTO createFeed(Long memberId, FeedRequestDTO.FeedDTO request);

    FeedResponseDTO.FeedResultDTO updateFeed(Long memberId, Long feedId, FeedRequestDTO.FeedDTO request);

    void deleteFeed(Long memberId, Long feedId);

    FeedResponseDTO.FeedHeartResultDTO toggleHeart(Long memberId, FeedRequestDTO.FeedHeartDTO request);
}
