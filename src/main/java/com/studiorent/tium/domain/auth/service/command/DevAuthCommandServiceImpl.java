package com.studiorent.tium.domain.auth.service.command;

import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.RefreshTokenService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.security.jwt.JwtProperties;
import com.studiorent.tium.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DevAuthCommandServiceImpl implements DevAuthCommandService {

    private static final Provider DEV_PROVIDER = Provider.KAKAO;

    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO.DevVerifyResultDTO verifyToken(AuthRequestDTO.DevVerifyDTO request) {
        String token = request.token();

        boolean expired = jwtProvider.isExpired(token);
        boolean validAsAccess = jwtProvider.validateAccessToken(token);
        boolean validAsRefresh = jwtProvider.validateRefreshToken(token);

        Long memberId = null;
        Boolean matchesStored = null;

        if (validAsAccess || validAsRefresh) {
            memberId = jwtProvider.getMemberId(token);

            if (validAsRefresh) {
                matchesStored = refreshTokenService.matches(memberId, token);
            }
        }

        return new AuthResponseDTO.DevVerifyResultDTO(
                expired, validAsAccess, validAsRefresh, memberId, matchesStored,
                verdict(expired, validAsAccess, validAsRefresh, matchesStored));
    }

    private String verdict(boolean expired, boolean validAsAccess, boolean validAsRefresh,
                           Boolean matchesStored) {
        if (expired) {
            return "만료된 토큰입니다. 재발급 API는 AUTH4013을 반환합니다.";
        }
        if (validAsAccess) {
            return "유효한 access 토큰입니다. Authorization 헤더에 쓸 수 있습니다.";
        }
        if (validAsRefresh) {
            return Boolean.TRUE.equals(matchesStored)
                    ? "유효한 refresh 토큰이고 DB 저장값과 일치합니다. 재발급에 쓸 수 있습니다."
                    : "서명은 유효한 refresh 토큰이지만 DB 저장값과 다릅니다. "
                      + "로그아웃했거나 이미 재발급되어 폐기된 토큰입니다. 재발급 시 AUTH4012입니다.";
        }
        return "서명이 맞지 않거나 형식이 잘못된 토큰입니다. type claim이 없는 구버전 토큰일 수도 있습니다.";
    }

    @Override
    @Transactional
    public AuthResponseDTO.DevTokenResultDTO issueToken(AuthRequestDTO.DevTokenDTO request) {
        Optional<Member> existing = memberRepository
                .findByProviderAndProviderId(DEV_PROVIDER, request.providerId());

        boolean isNewMember = existing.isEmpty();
        Member member = existing.orElseGet(() -> memberRepository.save(Member.builder()
                .provider(DEV_PROVIDER)
                .providerId(request.providerId())
                .build()));

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());

        refreshTokenService.save(member.getId(), refreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));

        return new AuthResponseDTO.DevTokenResultDTO(
                member.getId(), accessToken, refreshToken, isNewMember, member.isOnboardingCompleted());
    }
}
