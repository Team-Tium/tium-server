package com.studiorent.tium.domain.auth.converter;

import com.studiorent.tium.domain.auth.client.SocialUserInfo;
import com.studiorent.tium.domain.auth.dto.AuthResponseDTO;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Provider;

public class AuthConverter {

    public static Member toMember(Provider provider, SocialUserInfo userInfo) {
        return Member.builder()
                .provider(provider)
                .providerId(userInfo.providerId())
                .email(userInfo.email())
                .build();
    }

    public static AuthResponseDTO.ReissueResultDTO toReissueResult(String accessToken, String refreshToken) {
        return new AuthResponseDTO.ReissueResultDTO(accessToken, refreshToken);
    }

    public static AuthResponseDTO.WithdrawalResultDTO toWithdrawalResult(Member member) {
        return new AuthResponseDTO.WithdrawalResultDTO(member.getId(), member.getWithdrawnAt());
    }

    public static AuthResponseDTO.LoginResultDTO toLoginResult(Member member,
                                                               String accessToken,
                                                               String refreshToken,
                                                               boolean isNewMember) {
        return new AuthResponseDTO.LoginResultDTO(
                member.getId(),
                accessToken,
                refreshToken,
                isNewMember,
                member.isOnboardingCompleted()
        );
    }
}
