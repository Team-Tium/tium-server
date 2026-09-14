package com.studiorent.tium.domain.member.entity;

import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class MemberAuditingTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 회원_저장시_createdAt과_updatedAt이_자동으로_채워진다() {
        Member saved = memberRepository.saveAndFlush(Member.builder()
                .provider(Provider.KAKAO)
                .providerId("auditing-test-" + System.nanoTime())
                .build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
