package com.studiorent.tium.domain.auth;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
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
class AuthReissueLogoutTest {

    @Autowired private AuthCommandService authCommandService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JwtProperties jwtProperties;

    private Long memberId;
    private String refreshToken;

    @BeforeEach
    void 로그인된_상태를_만든다() {
        Member member = memberRepository.save(Member.builder()
                .provider(Provider.KAKAO)
                .providerId("reissue-test-" + System.nanoTime())
                .build());
        memberId = member.getId();
        refreshToken = jwtProvider.createRefreshToken(memberId);
        refreshTokenService.save(memberId, refreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));
    }

    @Test
    void 저장된_refresh_토큰으로_재발급된다() {
        AuthResponseDTO.ReissueResultDTO result =
                authCommandService.reissue(new AuthRequestDTO.ReissueDTO(refreshToken));

        assertThat(jwtProvider.validateAccessToken(result.accessToken())).isTrue();
        assertThat(jwtProvider.validateRefreshToken(result.refreshToken())).isTrue();
        assertThat(jwtProvider.getMemberId(result.accessToken())).isEqualTo(memberId);
    }

    @Test
    void DB에_없는_refresh_토큰은_재발급되지_않는다() {
        String other = jwtProvider.createRefreshToken(memberId + 99999);

        assertThatThrownBy(() -> authCommandService.reissue(new AuthRequestDTO.ReissueDTO(other)))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void access_토큰으로는_재발급할_수_없다() {
        String accessToken = jwtProvider.createAccessToken(memberId);

        assertThatThrownBy(() -> authCommandService.reissue(new AuthRequestDTO.ReissueDTO(accessToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void refreshToken이_비면_AUTH4012다() {
        assertThatThrownBy(() -> authCommandService.reissue(new AuthRequestDTO.ReissueDTO("")))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void 로그아웃하면_기존_refresh_토큰으로_재발급할_수_없다() {
        authCommandService.logout(memberId);

        assertThatThrownBy(() -> authCommandService.reissue(new AuthRequestDTO.ReissueDTO(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .extracting("baseCode").isEqualTo(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
    }

    @Test
    void 로그아웃은_여러번_해도_성공한다() {
        authCommandService.logout(memberId);
        authCommandService.logout(memberId);

        assertThat(refreshTokenService.matches(memberId, refreshToken)).isFalse();
    }
}
