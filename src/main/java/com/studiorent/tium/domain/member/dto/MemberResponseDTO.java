package com.studiorent.tium.domain.member.dto;

import com.studiorent.tium.domain.member.entity.enums.Gender;

import java.time.LocalDate;

public class MemberResponseDTO {

    /**
     * 내 프로필. 조회(GET /users/me)와 온보딩 저장(POST /onboarding/profile)의 응답이 같은 구조라 하나로 쓴다.
     * 온보딩 전에는 memberId / email / onboardingCompleted 외에는 전부 null이다.
     */
    public record MyProfileDTO(
            Long memberId,
            String name,
            String email,
            String phoneNumber,
            String address,
            Gender gender,
            LocalDate birthDate,
            String introduction,
            Boolean onboardingCompleted
    ) {
    }
}
