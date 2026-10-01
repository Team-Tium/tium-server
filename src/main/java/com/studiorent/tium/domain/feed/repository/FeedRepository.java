package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed, Long> {

    Optional<Feed> findByIdAndDeleteYn(Long id, String deleteYn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Feed> findWithLockByIdAndDeleteYn(Long id, String deleteYn);

    @Query("""
            select feed
            from Feed feed
            where feed.deleteYn = 'N'
              and (:cursorCreatedAt is null
                   or feed.createdAt < :cursorCreatedAt
                   or (feed.createdAt = :cursorCreatedAt and feed.id < :cursorId))
            order by feed.createdAt desc, feed.id desc
            """)
    List<Feed> findLatestFeeds(
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select feed
            from Feed feed
            where feed.deleteYn = 'N'
              and feed.createdAt >= :windowStart
              and (:cursorHeart is null
                   or feed.heart < :cursorHeart
                   or (feed.heart = :cursorHeart and feed.createdAt < :cursorCreatedAt)
                   or (feed.heart = :cursorHeart and feed.createdAt = :cursorCreatedAt and feed.id < :cursorId))
            order by feed.heart desc, feed.createdAt desc, feed.id desc
            """)
    List<Feed> findHeartFeeds(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("cursorHeart") Long cursorHeart,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
