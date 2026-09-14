package com.studiorent.tium.domain.member.entity.enums;

import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;

import java.util.Arrays;

/**
 * 소셜 로그인 제공자.
 * (provider + providerId) 조합이 회원을 식별한다. 같은 이메일이어도 provider가 다르면 다른 계정이다.
 */
public enum Provider {

    KAKAO,
    NAVER,
    GOOGLE;

    /**
     * PathVariable로 들어온 문자열(`kakao`, `naver`, `google`)을 enum으로 변환한다.
     * 지원하지 않는 값이면 AUTH4003.
     */
    public static Provider from(String value) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorStatus.AUTH_UNSUPPORTED_PROVIDER));
    }
}
