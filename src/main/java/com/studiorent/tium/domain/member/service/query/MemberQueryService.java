package com.studiorent.tium.domain.member.service.query;

import com.studiorent.tium.domain.member.dto.MemberResponseDTO;

public interface MemberQueryService {

    MemberResponseDTO.MyProfileDTO getMyProfile(Long memberId);
}
