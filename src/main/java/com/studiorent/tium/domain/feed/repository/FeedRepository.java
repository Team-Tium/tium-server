package com.studiorent.tium.domain.feed.repository;

import com.studiorent.tium.domain.feed.entity.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, Long> {
}
