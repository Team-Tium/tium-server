package com.studiorent.tium.domain.member.converter;

import com.studiorent.tium.domain.member.dto.MemberResponseDTO;
import com.studiorent.tium.domain.member.entity.Member;

public class MemberConverter {

    public static MemberResponseDTO.MyProfileDTO toMyProfile(Member member) {
        return new MemberResponseDTO.MyProfileDTO(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getPhoneNumber(),
                member.getAddress(),
                member.getGender(),
                member.getBirthDate(),
                member.getIntroduction(),
                member.isOnboardingCompleted()
        );
    }
}
