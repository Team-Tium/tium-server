package com.studiorent.tium.domain.auth;

import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.RefreshTokenService;
import com.studiorent.tium.domain.auth.service.command.AuthCommandService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import com.studiorent.tium.global.security.jwt.JwtProperties;
import com.studiorent.tium.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AuthWithdrawalTest {

    @Autowired private AuthCommandService authCommandService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JwtProperties jwtProperties;

    private Long memberId;
    private String socialId;
    private String refreshToken;

    @BeforeEach
    void 로그인된_회원을_만든다() {
        socialId = "withdrawal-test-" + System.nanoTime();
        Member member = memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(socialId)
                .build());
        memberId = member.getId();
        refreshToken = jwtProvider.createRefreshToken(memberId);
        refreshTokenService.save(memberId, refreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));
    }

    @Test
    void 탈퇴하면_withdrawnAt이_기록되고_회원_행은_남는다() {
        AuthResponseDTO.WithdrawalResultDTO result = authCommandService.withdraw(memberId);

        assertThat(result.memberId()).isEqualTo(memberId);
        assertThat(result.withdrawnAt()).isNotNull();
        assertThat(memberRepository.findById(memberId)).isPresent();
    }

    @Test
    void 탈퇴하면_providerId가_UUID로_덮어써진다() {
        authCommandService.withdraw(memberId);

        Member withdrawn = memberRepository.findById(memberId).orElseThrow();
        assertThat(withdrawn.getProviderId()).isNotEqualTo(socialId);
        assertThat(withdrawn.isWithdrawn()).isTrue();
    }

    @Test
    void 탈퇴하면_같은_소셜_계정으로_다시_가입할_수_있다() {
        authCommandService.withdraw(memberId);

        assertThat(memberRepository.findByProviderAndProviderId(Provider.KAKAO, socialId)).isEmpty();

        Member rejoined = memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId(socialId)
                .build());

        assertThat(rejoined.getId()).isNotEqualTo(memberId);
    }

    @Test
    void 탈퇴하면_refresh_토큰이_삭제된다() {
        authCommandService.withdraw(memberId);

        assertThat(refreshTokenService.matches(memberId, refreshToken)).isFalse();
    }

    @Test
    void 탈퇴한_회원은_재발급할_수_없다() {
        String liveToken = jwtProvider.createRefreshToken(memberId);
        refreshTokenService.save(memberId, liveToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));

        authCommandService.withdraw(memberId);

        assertThatThrownBy(() -> authCommandService.reissue(
                new com.studiorent.tium.domain.auth.dto.AuthRequestDTO.ReissueDTO(liveToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void 탈퇴_직후_남아있던_refresh로도_갱신을_이어갈_수_없다() {
        authCommandService.withdraw(memberId);
        refreshTokenService.save(memberId, refreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));

        assertThatThrownBy(() -> authCommandService.reissue(
                new com.studiorent.tium.domain.auth.dto.AuthRequestDTO.ReissueDTO(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void 없는_회원을_탈퇴시키면_AUTH4041이다() {
        assertThatThrownBy(() -> authCommandService.withdraw(memberId + 999999))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_MEMBER_NOT_FOUND);
    }

    @Test
    void 두_번_탈퇴해도_최초_탈퇴_시각이_유지된다() {
        LocalDateTime first = authCommandService.withdraw(memberId).withdrawnAt();
        LocalDateTime second = authCommandService.withdraw(memberId).withdrawnAt();

        assertThat(second).isEqualTo(first);
    }
}
