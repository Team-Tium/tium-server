package com.studiorent.tium.domain.member.service.query;

import com.studiorent.tium.domain.member.converter.MemberConverter;
import com.studiorent.tium.domain.member.dto.MemberResponseDTO;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberQueryServiceImpl implements MemberQueryService {

    private final MemberRepository memberRepository;

    /**
     * 온보딩 완료 여부와 무관하게 호출할 수 있다.
     * 온보딩 전이면 이름·주소 같은 값이 전부 null로 나간다.
     */
    @Override
    @Transactional(readOnly = true)
    public MemberResponseDTO.MyProfileDTO getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorStatus.MEMBER_WITHDRAWN);
        }

        return MemberConverter.toMyProfile(member);
    }
}
