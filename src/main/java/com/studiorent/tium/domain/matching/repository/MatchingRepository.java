package com.studiorent.tium.domain.matching.repository;

import com.studiorent.tium.domain.matching.entity.Matching;
import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface MatchingRepository extends JpaRepository<Matching, Long> {

    boolean existsByMemberId(Long memberId);

    /** 가장 오래 기다린 자리를 잠그고 읽는다. 트랜잭션이 끝날 때까지 다른 요청은 이 행을 가져가지 못한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Matching> findFirstByTypeOrderByIdAsc(MatchingType type);

    long deleteByMemberId(Long memberId);
}
