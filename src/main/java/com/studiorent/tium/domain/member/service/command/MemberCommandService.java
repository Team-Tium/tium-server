package com.studiorent.tium.domain.member.service.command;

import com.studiorent.tium.domain.member.dto.MemberRequestDTO;
import com.studiorent.tium.domain.member.dto.MemberResponseDTO;

public interface MemberCommandService {

    MemberResponseDTO.MyProfileDTO saveOnboardingProfile(Long memberId,
                                                         MemberRequestDTO.OnboardingProfileDTO request);
}
