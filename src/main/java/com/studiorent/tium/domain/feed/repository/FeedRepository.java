package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import com.studiorent.tium.domain.feed.repository.projection.FeedListProjection;
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
            select feed.id as feedId,
                   feed.memberId as memberId,
                   author.name as nickname,
                   author.profileImageUrl as profileImageUrl,
                   feed.content as content,
                   feed.fileId as fileId,
                   feed.heart as heart,
                   feed.createdAt as createdAt
            from Feed feed
            join Member author on author.id = feed.memberId
            where feed.deleteYn = 'N'
              and (:ownerMemberId is null or feed.memberId = :ownerMemberId)
              and (:cursorCreatedAt is null
                   or feed.createdAt < :cursorCreatedAt
                   or (feed.createdAt = :cursorCreatedAt and feed.id < :cursorId))
            order by feed.createdAt desc, feed.id desc
            """)
    List<FeedListProjection> findLatestFeeds(
            @Param("ownerMemberId") Long ownerMemberId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select feed.id as feedId,
                   feed.memberId as memberId,
                   author.name as nickname,
                   author.profileImageUrl as profileImageUrl,
                   feed.content as content,
                   feed.fileId as fileId,
                   feed.heart as heart,
                   feed.createdAt as createdAt
            from Feed feed
            join Member author on author.id = feed.memberId
            where feed.deleteYn = 'N'
              and (:ownerMemberId is null or feed.memberId = :ownerMemberId)
              and feed.createdAt >= :windowStart
              and (:cursorHeart is null
                   or feed.heart < :cursorHeart
                   or (feed.heart = :cursorHeart and feed.createdAt < :cursorCreatedAt)
                   or (feed.heart = :cursorHeart and feed.createdAt = :cursorCreatedAt and feed.id < :cursorId))
            order by feed.heart desc, feed.createdAt desc, feed.id desc
            """)
    List<FeedListProjection> findHeartFeeds(
            @Param("ownerMemberId") Long ownerMemberId,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("cursorHeart") Long cursorHeart,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
