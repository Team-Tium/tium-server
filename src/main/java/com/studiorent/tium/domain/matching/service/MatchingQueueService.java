package com.studiorent.tium.domain.matching.service;

import com.studiorent.tium.domain.matching.entity.Matching;
import com.studiorent.tium.domain.matching.entity.enums.MatchingType;
import com.studiorent.tium.domain.matching.repository.MatchingRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 매칭 대기열 저장소 접근을 모은 클래스. 대기열을 다른 저장소로 옮길 때 이 클래스만 바꾼다. */
@Service
@RequiredArgsConstructor
public class MatchingQueueService {

    private final MatchingRepository matchingRepository;

    @Transactional(readOnly = true)
    public boolean isWaiting(Long memberId) {
        return matchingRepository.existsByMemberId(memberId);
    }

    /**
     * 가장 오래 기다린 회원을 대기열에서 꺼내 그 회원 ID를 돌려준다. 아무도 없으면 빈 값.
     *
     * <p>꺼낼 행을 잠그고 읽는다. 동시에 같은 행을 노린 요청은 이 트랜잭션이 끝날 때까지 기다렸다가
     * 이미 지워진 행을 건너뛰므로, 한 회원이 두 번 꺼내지지 않는다.
     */
    @Transactional
    public Optional<Long> pollOldest(MatchingType type) {
        return matchingRepository.findFirstByTypeOrderByIdAsc(type)
                .map(waiting -> {
                    matchingRepository.delete(waiting);
                    return waiting.getMemberId();
                });
    }

    /**
     * 대기열에 자리를 만든다.
     *
     * <p>같은 회원의 요청 두 개가 동시에 대기 여부 확인을 통과하면 두 번째 삽입이 회원 유니크 제약에 걸린다.
     * 그 충돌을 이미 대기 중인 것으로 보고 {@code MATCHING4091}로 바꾼다.
     */
    @Transactional
    public void add(Long memberId, MatchingType type) {
        try {
            matchingRepository.saveAndFlush(Matching.enqueue(memberId, type));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorStatus.MATCHING_ALREADY_WAITING);
        }
    }

    /** 대기 중이던 자리를 지운다. 지운 자리가 없으면 false를 돌려준다. */
    @Transactional
    public boolean remove(Long memberId) {
        return matchingRepository.deleteByMemberId(memberId) > 0;
    }
}
