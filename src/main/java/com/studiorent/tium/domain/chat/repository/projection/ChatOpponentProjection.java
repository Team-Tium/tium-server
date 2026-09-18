package com.studiorent.tium.domain.chat.repository.projection;

import java.time.LocalDateTime;

/**
 * 내역 조회에서 상대방 한 줄.
 *
 * <p>{@code opponentLastReadMessageId}는 응답에 나가지 않는다. 메시지마다 {@code isRead}를
 * 계산하는 기준이라 함께 뽑아온다 — 이 값이 없으면 메시지 수만큼 조회가 더 필요해진다.
 */
public interface ChatOpponentProjection {

    Long getOpponentId();

    String getOpponentNickname();

    String getOpponentProfileImageUrl();

    /** 나간 시각 원본. 판단은 자바에서 한다. */
    LocalDateTime getOpponentLeftAt();

    Long getOpponentLastReadMessageId();
}
