package com.studiorent.tium.domain.feed.service.query;

import com.studiorent.tium.domain.feed.converter.FeedConverter;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.dto.FeedSortType;
import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.exception.FeedErrorStatus;
import com.studiorent.tium.domain.feed.repository.FeedLikeLogRepository;
import com.studiorent.tium.domain.feed.repository.FeedRepository;
import com.studiorent.tium.domain.feed.repository.projection.FeedListProjection;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedQueryServiceImpl implements FeedQueryService {

    private static final int FIRST_PAGE_SIZE = 6;
    private static final int NEXT_PAGE_SIZE = 3;
    private static final int HEART_WINDOW_HOURS = 6;
    private static final String CURSOR_SEPARATOR = "\\|";

    private final FeedRepository feedRepository;
    private final FeedLikeLogRepository feedLikeLogRepository;

    @Override
    @Transactional(readOnly = true)
    public FeedResponseDTO.FeedListDTO getFeeds(Long memberId, FeedSortType sort, String cursor) {
        return getFeedList(memberId, sort, cursor, null);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedResponseDTO.FeedListDTO getMyFeeds(Long memberId, FeedSortType sort, String cursor) {
        return getFeedList(memberId, sort, cursor, memberId);
    }

    private FeedResponseDTO.FeedListDTO getFeedList(Long memberId, FeedSortType sort, String cursor, Long ownerMemberId) {
        FeedCursor feedCursor = FeedCursor.decode(sort, cursor);
        boolean firstPage = feedCursor == null;
        if (sort == FeedSortType.HEART && firstPage) {
            feedCursor = FeedCursor.firstHeartCursor();
        }

        int size = firstPage ? FIRST_PAGE_SIZE : NEXT_PAGE_SIZE;

        List<FeedListProjection> rows = findFeeds(sort, feedCursor, ownerMemberId, size + 1);
        boolean hasNext = rows.size() > size;
        List<FeedListProjection> page = hasNext ? rows.subList(0, size) : rows;

        Set<Long> likedFeedIds = findLikedFeedIds(memberId, page);
        List<FeedResponseDTO.FeedListItemDTO> feeds = page.stream()
                .map(feed -> FeedConverter.toFeedListItem(feed, likedFeedIds.contains(feed.getFeedId())))
                .toList();

        String nextCursor = hasNext ? FeedCursor.encode(sort, page.get(page.size() - 1), feedCursor) : null;

        return new FeedResponseDTO.FeedListDTO(feeds, hasNext, nextCursor);
    }

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

    private List<FeedListProjection> findFeeds(FeedSortType sort, FeedCursor cursor, Long ownerMemberId, int limit) {
        if (sort == FeedSortType.HEART) {
            LocalDateTime windowStart = cursor == null
                    ? LocalDateTime.now().minusHours(HEART_WINDOW_HOURS)
                    : cursor.windowStart();

            return feedRepository.findHeartFeeds(
                    ownerMemberId,
                    windowStart,
                    cursor == null ? null : cursor.heart(),
                    cursor == null ? null : cursor.createdAt(),
                    cursor == null ? null : cursor.feedId(),
                    PageRequest.of(0, limit));
        }

        return feedRepository.findLatestFeeds(
                ownerMemberId,
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.feedId(),
                PageRequest.of(0, limit));
    }

    private Set<Long> findLikedFeedIds(Long memberId, List<FeedListProjection> feeds) {
        if (feeds.isEmpty()) {
            return Set.of();
        }

        List<Long> feedIds = feeds.stream()
                .map(FeedListProjection::getFeedId)
                .toList();

        return feedLikeLogRepository.findActiveLikedFeedIds(memberId, feedIds);
    }

    private record FeedCursor(
            FeedSortType sort,
            LocalDateTime windowStart,
            Long heart,
            LocalDateTime createdAt,
            Long feedId
    ) {

        private static FeedCursor decode(FeedSortType sort, String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }

            try {
                String payload = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] parts = payload.split(CURSOR_SEPARATOR, -1);
                FeedSortType cursorSort = FeedSortType.valueOf(parts[0]);

                if (cursorSort != sort) {
                    throw invalidCursor();
                }

                if (cursorSort == FeedSortType.LATEST && parts.length == 3) {
                    return new FeedCursor(
                            cursorSort,
                            null,
                            null,
                            LocalDateTime.parse(parts[1]),
                            Long.parseLong(parts[2]));
                }

                if (cursorSort == FeedSortType.HEART && parts.length == 5) {
                    return new FeedCursor(
                            cursorSort,
                            LocalDateTime.parse(parts[1]),
                            Long.parseLong(parts[2]),
                            LocalDateTime.parse(parts[3]),
                            Long.parseLong(parts[4]));
                }

                throw invalidCursor();
            } catch (IllegalArgumentException | DateTimeParseException ex) {
                throw invalidCursor();
            }
        }

        private static BusinessException invalidCursor() {
            return new BusinessException(ErrorStatus.INVALID_CURSOR);
        }

        private static FeedCursor firstHeartCursor() {
            return new FeedCursor(
                    FeedSortType.HEART,
                    LocalDateTime.now().minusHours(HEART_WINDOW_HOURS),
                    null,
                    null,
                    null);
        }

        private static String encode(FeedSortType sort, FeedListProjection feed, FeedCursor previousCursor) {
            String payload;
            if (sort == FeedSortType.HEART) {
                payload = String.join("|",
                        sort.name(),
                        previousCursor.windowStart().toString(),
                        feed.getHeart().toString(),
                        feed.getCreatedAt().toString(),
                        feed.getFeedId().toString());
            } else {
                payload = String.join("|",
                        sort.name(),
                        feed.getCreatedAt().toString(),
                        feed.getFeedId().toString());
            }

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }
    }
}
