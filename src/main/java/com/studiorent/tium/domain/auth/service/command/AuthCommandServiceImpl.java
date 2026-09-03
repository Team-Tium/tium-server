package com.studiorent.tium.domain.auth.service.command;

import com.studiorent.tium.domain.auth.client.SocialClientResolver;
import com.studiorent.tium.domain.auth.client.SocialUserInfo;
import com.studiorent.tium.domain.auth.converter.AuthConverter;
import com.studiorent.tium.domain.auth.dto.AuthRequestDTO;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.auth.service.RefreshTokenService;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import com.studiorent.tium.global.security.jwt.JwtProperties;
import com.studiorent.tium.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthCommandServiceImpl implements AuthCommandService {

    private final SocialClientResolver socialClientResolver;
    private final MemberRepository memberRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public AuthResponseDTO.LoginResultDTO login(String provider, AuthRequestDTO.LoginDTO request) {
        Provider socialProvider = Provider.from(provider);
        String authorizationCode = resolveAuthorizationCode(request);

        SocialUserInfo userInfo = socialClientResolver.resolve(socialProvider)
                .fetchUserInfo(authorizationCode);

        Optional<Member> existing = memberRepository
                .findByProviderAndProviderId(socialProvider, userInfo.providerId());

        boolean isNewMember = existing.isEmpty();
        Member member = existing.orElseGet(
                () -> memberRepository.save(AuthConverter.toMember(socialProvider, userInfo)));

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = jwtProvider.createRefreshToken(member.getId());

        refreshTokenService.save(member.getId(), refreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));

        return AuthConverter.toLoginResult(member, accessToken, refreshToken, isNewMember);
    }

    @Override
    @Transactional
    public AuthResponseDTO.ReissueResultDTO reissue(AuthRequestDTO.ReissueDTO request) {
        String refreshToken = resolveRefreshToken(request);

        if (jwtProvider.isExpired(refreshToken)) {
            throw new BusinessException(ErrorStatus.AUTH_EXPIRED_REFRESH_TOKEN);
        }
        if (!jwtProvider.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }

        Long memberId = jwtProvider.getMemberId(refreshToken);

        if (!refreshTokenService.matches(memberId, refreshToken)) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(memberId);
        String newRefreshToken = jwtProvider.createRefreshToken(memberId);

        refreshTokenService.save(memberId, newRefreshToken,
                LocalDateTime.now().plus(jwtProperties.refreshTokenExpiration()));

        return AuthConverter.toReissueResult(newAccessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(Long memberId) {
        refreshTokenService.delete(memberId);
    }

    private String resolveRefreshToken(AuthRequestDTO.ReissueDTO request) {
        if (request == null || !StringUtils.hasText(request.refreshToken())) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_REFRESH_TOKEN);
        }
        return request.refreshToken();
    }

    private String resolveAuthorizationCode(AuthRequestDTO.LoginDTO request) {
        if (request == null || !StringUtils.hasText(request.authorizationCode())) {
            throw new BusinessException(ErrorStatus.AUTH_SOCIAL_CREDENTIAL_REQUIRED);
        }
        return request.authorizationCode();
    }
}
