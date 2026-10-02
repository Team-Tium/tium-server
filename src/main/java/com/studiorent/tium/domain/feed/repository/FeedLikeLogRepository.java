package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.entity.FeedLikeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FeedLikeLogRepository extends JpaRepository<FeedLikeLog, Long> {

    Optional<FeedLikeLog> findByFeedAndMemberId(Feed feed, Long memberId);

    @Query("""
            select feedLikeLog
            from FeedLikeLog feedLikeLog
            join fetch feedLikeLog.feed feed
            where feed.memberId = :memberId
              and feed.deleteYn = 'N'
              and feedLikeLog.heartYn = 'Y'
            order by feedLikeLog.heartedAt desc
            """)
    List<FeedLikeLog> findActiveLikeHistoryByFeedOwnerId(@Param("memberId") Long memberId);

    @Query("""
            select feedLikeLog.feed.id
            from FeedLikeLog feedLikeLog
            where feedLikeLog.memberId = :memberId
              and feedLikeLog.heartYn = 'Y'
              and feedLikeLog.feed.id in :feedIds
            """)
    Set<Long> findActiveLikedFeedIds(
            @Param("memberId") Long memberId,
            @Param("feedIds") List<Long> feedIds);
}
