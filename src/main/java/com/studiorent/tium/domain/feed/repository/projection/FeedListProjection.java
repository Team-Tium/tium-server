package com.studiorent.tium.domain.feed.repository.projection;

import java.time.LocalDateTime;

public interface FeedListProjection {

    Long getFeedId();

    Long getMemberId();

    String getNickname();

    String getProfileImageUrl();

    String getContent();

    Long getFileId();

    Long getHeart();

    LocalDateTime getCreatedAt();
}
