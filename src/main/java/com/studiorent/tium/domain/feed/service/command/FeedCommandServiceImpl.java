package com.studiorent.tium.domain.feed.service.command;

import com.studiorent.tium.domain.feed.converter.FeedConverter;
import com.studiorent.tium.domain.feed.dto.FeedRequestDTO;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.entity.FeedLikeLog;
import com.studiorent.tium.domain.feed.exception.FeedErrorStatus;
import com.studiorent.tium.domain.feed.repository.FeedLikeLogRepository;
import com.studiorent.tium.domain.feed.repository.FeedRepository;
import com.studiorent.tium.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedCommandServiceImpl implements FeedCommandService {

    private final FeedRepository feedRepository;
    private final FeedLikeLogRepository feedLikeLogRepository;

    @Override
    @Transactional
    public FeedResponseDTO.FeedResultDTO createFeed(Long memberId, FeedRequestDTO.FeedDTO request) {
        Feed feed = feedRepository.save(FeedConverter.toFeed(memberId, request));

        return FeedConverter.toFeedResult(feed);
    }

    @Override
    @Transactional
    public FeedResponseDTO.FeedResultDTO updateFeed(Long memberId, Long feedId, FeedRequestDTO.FeedDTO request) {
        Feed feed = findActiveFeed(feedId);
        validateOwner(feed, memberId);

        feed.updateContent(request.content());

        return FeedConverter.toFeedResult(feed);
    }

    @Override
    @Transactional
    public void deleteFeed(Long memberId, Long feedId) {
        Feed feed = findActiveFeed(feedId);
        validateOwner(feed, memberId);

        feed.delete();
    }

    @Override
    @Transactional
    public FeedResponseDTO.FeedHeartResultDTO toggleHeart(Long memberId, FeedRequestDTO.FeedHeartDTO request) {
        Feed feed = feedRepository.findWithLockByIdAndDeleteYn(request.feedId(), "N")
                .orElseThrow(() -> new BusinessException(FeedErrorStatus.FEED_NOT_FOUND));

        FeedLikeLog feedLikeLog = feedLikeLogRepository.findByFeedAndMemberId(feed, memberId)
                .orElse(null);

        if (feedLikeLog == null) {
            feedLikeLog = feedLikeLogRepository.save(FeedLikeLog.create(feed, memberId));
            feed.increaseHeart();
        } else if (feedLikeLog.isHearted()) {
            feedLikeLog.unheart();
            feed.decreaseHeart();
        } else {
            feedLikeLog.heart();
            feed.increaseHeart();
        }

        return FeedConverter.toFeedHeartResult(feed, feedLikeLog);
    }

    private Feed findActiveFeed(Long feedId) {
        return feedRepository.findByIdAndDeleteYn(feedId, "N")
                .orElseThrow(() -> new BusinessException(FeedErrorStatus.FEED_NOT_FOUND));
    }

    private void validateOwner(Feed feed, Long memberId) {
        if (!feed.isOwnedBy(memberId)) {
            throw new BusinessException(FeedErrorStatus.FEED_FORBIDDEN);
        }
    }
}
