package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.entity.FeedLikeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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
}
