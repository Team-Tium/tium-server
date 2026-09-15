package com.studiorent.tium.domain.chat.repository;

import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.repository.projection.ChatOpponentProjection;
import com.studiorent.tium.domain.chat.repository.projection.ChatRoomListProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    /** 요청자가 이 방의 참여자인지 확인할 때 쓴다. 나간 참여자도 행은 남아 있어 함께 조회된다. */
    Optional<ChatRoomMember> findByChatRoomIdAndMemberId(Long chatRoomId, Long memberId);

    /**
     * 상대 참여 행과 회원 정보를 한 번에 읽는다. 내역 조회에서 상대 정보와 나감 여부를 채울 때 쓴다.
     *
     * <p>{@code member_id}에 연관 매핑이 없어 직접 조인한다. 1:1 방이라 결과는 최대 한 줄이다.
     */
    @Query(value = """
            SELECT
                om.member_id        AS opponentId,
                m.name              AS opponentNickname,
                m.profile_image_url AS opponentProfileImageUrl,
                om.left_at              AS opponentLeftAt,
                om.last_read_message_id AS opponentLastReadMessageId
            FROM chat_room_member om
            JOIN member m ON m.id = om.member_id
            WHERE om.chat_room_id = :roomId
              AND om.member_id <> :memberId
            """, nativeQuery = true)
    Optional<ChatOpponentProjection> findOpponent(@Param("roomId") Long roomId,
                                                  @Param("memberId") Long memberId);

    /**
     * {@code GET /chats} 목록 한 페이지를 쿼리 한 번으로 읽어온다.
     *
     * <p>내 참여 행에서 출발하는 이유는 {@code (member_id, left_at)} 인덱스를 타기 위해서다.
     * 방 테이블에서 시작하면 전체 방을 훑게 된다.
     *
     * <p>조인 넷의 성격이 다르다.
     * <ul>
     *   <li>{@code chat_room} — 매핑된 FK</li>
     *   <li>{@code chat_message} — {@code last_message_id}가 FK가 아니라 조건을 직접 준다.
     *       PK 조인이라 방당 한 줄이고, INNER JOIN이라 메시지 없는 방이 여기서 걸러진다</li>
     *   <li>{@code chat_room_member om} — 같은 테이블 자기 조인. 방당 2행이라 {@code <>} 하나로 상대가 특정된다</li>
     *   <li>{@code member} — 연관 매핑이 없어 직접 조인</li>
     * </ul>
     *
     * <p>unreadCount의 NULL 둘을 주의해서 다룬다.
     * {@code COALESCE(last_read_message_id, 0)}은 한 번도 안 읽은 경우(NULL)를 0으로 바꾼다.
     * 그대로 두면 {@code id > NULL}이 NULL이 되어 0건으로 세어진다.
     * {@code sender_id IS NOT NULL}은 SYSTEM 메시지를 뺀다.
     *
     * @param limit size + 1을 넘긴다. 넘치면 다음 페이지가 있다는 뜻이다.
     */
    @Query(value = """
            SELECT
                cr.id                   AS roomId,
                om.member_id            AS opponentId,
                m.name                  AS opponentNickname,
                m.profile_image_url     AS opponentProfileImageUrl,
                om.left_at              AS opponentLeftAt,
                msg.id                  AS lastMessageId,
                msg.content             AS lastMessageContent,
                msg.message_type        AS lastMessageType,
                msg.created_at          AS lastMessageSentAt,
                (SELECT COUNT(*)
                   FROM chat_message um
                  WHERE um.chat_room_id = cr.id
                    AND um.sender_id IS NOT NULL
                    AND um.sender_id <> :memberId
                    AND um.id > COALESCE(my.last_read_message_id, 0)) AS unreadCount
            FROM chat_room_member my
            JOIN chat_room        cr  ON cr.id = my.chat_room_id
            JOIN chat_message     msg ON msg.id = cr.last_message_id
            JOIN chat_room_member om  ON om.chat_room_id = cr.id
                                     AND om.member_id <> my.member_id
            JOIN member           m   ON m.id = om.member_id
            WHERE my.member_id = :memberId
              AND my.left_at IS NULL
              AND cr.last_message_id IS NOT NULL
              AND (:cursor IS NULL OR cr.last_message_id < :cursor)
            ORDER BY cr.last_message_id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ChatRoomListProjection> findRoomList(@Param("memberId") Long memberId,
                                             @Param("cursor") Long cursor,
                                             @Param("limit") int limit);
}
