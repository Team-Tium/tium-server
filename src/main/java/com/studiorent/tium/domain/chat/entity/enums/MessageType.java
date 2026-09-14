package com.studiorent.tium.domain.chat.entity.enums;

/**
 * 채팅 메시지 종류.
 *
 * <p>{@code IMAGE}와 {@code SYSTEM}은 값만 열어둔 상태다.
 * {@code POST /chats/{roomId}/messages}는 현재 {@code TEXT}만 받는다.
 * 상세는 docs/erd.md의 chat_message 절 참고.
 */
public enum MessageType {

    TEXT,

    /** 2차 범위. 첨부 테이블을 두지 않고 content에 이미지 URL을 담는다(메시지당 한 장). */
    IMAGE,

    /** 발신자가 없는 안내 메시지. 생성 규칙이 미정이라 아직 만드는 곳이 없다. */
    SYSTEM
}
