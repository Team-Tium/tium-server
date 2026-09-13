package com.studiorent.tium.domain.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "feed_like_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_feed_like_log_feed_member",
                columnNames = {"feed_id", "member_id"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedLikeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "hearted_at", nullable = false)
    private LocalDateTime heartedAt;

    @Builder.Default
    @Column(name = "heart_yn", nullable = false, length = 1)
    private String heartYn = "Y";

    public static FeedLikeLog create(Feed feed, Long memberId) {
        return FeedLikeLog.builder()
                .feed(feed)
                .memberId(memberId)
                .heartedAt(LocalDateTime.now())
                .heartYn("Y")
                .build();
    }

    public boolean isHearted() {
        return "Y".equals(this.heartYn);
    }

    public void heart() {
        this.heartYn = "Y";
        this.heartedAt = LocalDateTime.now();
    }

    public void unheart() {
        this.heartYn = "N";
        this.heartedAt = LocalDateTime.now();
    }
}
