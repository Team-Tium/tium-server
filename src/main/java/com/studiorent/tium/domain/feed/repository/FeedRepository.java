package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedRepository extends JpaRepository<Feed, Long> {

    Optional<Feed> findByIdAndDeleteYn(Long id, String deleteYn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Feed> findWithLockByIdAndDeleteYn(Long id, String deleteYn);
}
