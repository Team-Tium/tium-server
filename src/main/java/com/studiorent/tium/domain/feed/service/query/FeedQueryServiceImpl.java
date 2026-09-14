package com.studiorent.tium.domain.feed.service.query;

import com.studiorent.tium.domain.feed.converter.FeedConverter;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.exception.FeedErrorStatus;
import com.studiorent.tium.domain.feed.repository.FeedLikeLogRepository;
import com.studiorent.tium.domain.feed.repository.FeedRepository;
import com.studiorent.tium.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedQueryServiceImpl implements FeedQueryService {

    private final FeedRepository feedRepository;
    private final FeedLikeLogRepository feedLikeLogRepository;

    @Override
    @Transactional(readOnly = true)
    public FeedResponseDTO.FeedResultDTO getFeed(Long feedId) {
        return feedRepository.findByIdAndDeleteYn(feedId, "N")
                .map(FeedConverter::toFeedResult)
                .orElseThrow(() -> new BusinessException(FeedErrorStatus.FEED_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedResponseDTO.FeedHeartHistoryDTO> getMyFeedHeartHistory(Long memberId) {
        return feedLikeLogRepository.findActiveLikeHistoryByFeedOwnerId(memberId)
                .stream()
                .map(FeedConverter::toFeedHeartHistory)
                .toList();
    }
}
