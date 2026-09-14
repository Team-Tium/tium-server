package com.studiorent.tium.domain.feed.entity;

import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feed")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Feed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // TODO: Connect to file storage/domain when feed file upload is implemented.
    @Column(name = "file_id")
    private Long fileId;

    @Builder.Default
    @Column(name = "delete_yn", nullable = false, length = 1)
    private String deleteYn = "N";

    @Builder.Default
    @Column(nullable = false)
    private Long heart = 0L;

    public void updateContent(String content) {
        this.content = content;
    }

    public void delete() {
        this.deleteYn = "Y";
    }

    public boolean isDeleted() {
        return "Y".equals(this.deleteYn);
    }

    public boolean isOwnedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    public void increaseHeart() {
        this.heart++;
    }

    public void decreaseHeart() {
        if (this.heart > 0) {
            this.heart--;
        }
    }
}
