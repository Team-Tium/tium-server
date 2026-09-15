package com.studiorent.tium.domain.chat;

import com.studiorent.tium.domain.chat.dto.ChatResponseDTO;
import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.ChatRoom;
import com.studiorent.tium.domain.chat.entity.ChatRoomMember;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomMemberRepository;
import com.studiorent.tium.domain.chat.repository.ChatRoomRepository;
import com.studiorent.tium.domain.chat.service.query.ChatQueryService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 목록·내역 조회의 쿼리 수를 센다.
 *
 * <p>방 개수를 늘려도 쿼리 수가 그대로인지가 핵심이다. 방마다 조회가 붙으면 여기서 숫자가 따라 올라간다.
 */
@SpringBootTest
@Transactional
class ChatListQueryCountTest {

    private static final int ROOM_COUNT = 50;
    private static final int MESSAGES_PER_ROOM = 5;

    @Autowired private ChatQueryService chatQueryService;
    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EntityManager entityManager;

    private Long meId;
    private Long firstRoomId;

    @BeforeEach
    void 더미_데이터를_넣는다() {
        meId = 회원을_만든다("me").getId();

        for (int i = 0; i < ROOM_COUNT; i++) {
            Long opponentId = 회원을_만든다("opp" + i).getId();

            ChatRoom room = chatRoomRepository.save(ChatRoom.create(meId, opponentId));
            chatRoomMemberRepository.saveAll(List.of(
                    ChatRoomMember.join(room, meId),
                    ChatRoomMember.join(room, opponentId)
            ));

            ChatMessage last = null;
            for (int j = 0; j < MESSAGES_PER_ROOM; j++) {
                Long sender = (j % 2 == 0) ? opponentId : meId;
                last = chatMessageRepository.save(ChatMessage.ofText(room, sender, "메시지 " + j));
            }
            entityManager.flush();
            room.updateLastMessage(last.getId(), last.getSentAt());

            if (i == 0) {
                firstRoomId = room.getId();
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    private Member 회원을_만든다(String prefix) {
        return memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(prefix + "-" + System.nanoTime())
                .name(prefix)
                .build());
    }

    private Statistics 통계를_켠다() {
        Statistics stats = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();
        return stats;
    }

    @Test
    void 방_50개를_조회해도_쿼리는_한_번이다() {
        Statistics stats = 통계를_켠다();

        ChatResponseDTO.GetChatDTO result = chatQueryService.getChats(meId, null, 20);

        long queries = stats.getPrepareStatementCount();
        System.out.println("[목록] 방 " + ROOM_COUNT + "개 중 20개 조회 → 쿼리 " + queries + "번");

        assertThat(result.rooms()).hasSize(20);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isNotNull();
        assertThat(queries).isEqualTo(1);
    }

    @Test
    void 내역_조회는_메시지_개수와_무관하게_쿼리_세_번이다() {
        Statistics stats = 통계를_켠다();

        ChatResponseDTO.GetMessagesDTO result =
                chatQueryService.getMessages(meId, firstRoomId, null, null, 30);

        long queries = stats.getPrepareStatementCount();
        System.out.println("[내역] 메시지 " + MESSAGES_PER_ROOM + "건 조회 → 쿼리 " + queries + "번");

        assertThat(result.roomId()).isEqualTo(firstRoomId);
        assertThat(result.opponent()).isNotNull();
        assertThat(result.messages()).hasSize(MESSAGES_PER_ROOM);
        assertThat(queries).isEqualTo(3);
    }

    @Test
    void unreadCount는_상대가_보낸_안_읽은_메시지만_센다() {
        ChatResponseDTO.GetChatDTO result = chatQueryService.getChats(meId, null, 20);

        // 한 번도 안 읽었고 메시지 5건 중 상대가 보낸 건 3건(0, 2, 4번)이다
        assertThat(result.rooms())
                .allSatisfy(room -> assertThat(room.unreadCount()).isEqualTo(3L));
    }

    @Test
    void 실행계획을_찍는다() {
        @SuppressWarnings("unchecked")
        List<Object> plan = entityManager.createNativeQuery("""
                EXPLAIN ANALYZE
                SELECT cr.id, om.member_id, m.name, msg.id, msg.content,
                    (SELECT COUNT(*) FROM chat_message um
                      WHERE um.chat_room_id = cr.id
                        AND um.sender_id IS NOT NULL
                        AND um.sender_id <> :memberId
                        AND um.id > COALESCE(my.last_read_message_id, 0))
                FROM chat_room_member my
                JOIN chat_room        cr  ON cr.id = my.chat_room_id
                JOIN chat_message     msg ON msg.id = cr.last_message_id
                JOIN chat_room_member om  ON om.chat_room_id = cr.id
                                         AND om.member_id <> my.member_id
                JOIN member           m   ON m.id = om.member_id
                WHERE my.member_id = :memberId
                  AND my.left_at IS NULL
                  AND cr.last_message_id IS NOT NULL
                ORDER BY cr.last_message_id DESC
                LIMIT 21
                """).setParameter("memberId", meId).getResultList();

        System.out.println("[실행계획]");
        plan.forEach(System.out::println);
    }
}
