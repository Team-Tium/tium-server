package com.studiorent.tium.domain.matching.entity;

import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 대기열의 한 자리. 줄에 서면 행이 생기고, 취소하거나 짝이 정해지면 행을 지운다.
 * ID가 순증가라 ID 오름차순이 줄 선 순서다.
 *
 * <p>회원당 한 자리만 허용한다. 조회만으로 막으면 같은 회원이 동시에 두 번 요청할 때
 * 두 자리가 생길 수 있어 DB 제약으로 막는다.
 */
@Entity
@Table(
        name = "matching",
        uniqueConstraints = @UniqueConstraint(name = "uk_matching_member", columnNames = "member_id"),
        indexes = @Index(name = "idx_matching_type", columnList = "type")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Matching extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchingType type;

    public static Matching enqueue(Long memberId, MatchingType type) {
        return Matching.builder()
                .memberId(memberId)
                .type(type)
                .build();
    }
}
