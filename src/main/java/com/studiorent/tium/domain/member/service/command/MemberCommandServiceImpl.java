package com.studiorent.tium.domain.member.service.command;

import com.studiorent.tium.domain.member.converter.MemberConverter;
import com.studiorent.tium.domain.member.dto.MemberRequestDTO;
import com.studiorent.tium.domain.member.dto.MemberResponseDTO;
import com.studiorent.tium.domain.member.entity.Member;
import com.studiorent.tium.domain.member.entity.enums.Gender;
import com.studiorent.tium.domain.member.repository.MemberRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCommandServiceImpl implements MemberCommandService {

    private final MemberRepository memberRepository;

    /**
     * 소셜 로그인 시점에 이미 만들어진 회원 정보를 수정한다. 새 회원을 만들지 않는다.
     * 호출됐다는 사실 자체를 온보딩 완료로 보기 때문에 값을 하나도 보내지 않아도 완료 처리된다.
     */
    @Override
    @Transactional
    public MemberResponseDTO.MyProfileDTO saveOnboardingProfile(Long memberId,
                                                                MemberRequestDTO.OnboardingProfileDTO request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.MEMBER_NOT_FOUND));

        if (member.isWithdrawn()) {
            throw new BusinessException(ErrorStatus.MEMBER_WITHDRAWN);
        }

        member.updateProfile(
                request.name(),
                request.phoneNumber(),
                request.address(),
                toGender(request.gender()),
                request.birthDate(),
                request.introduction()
        );

        return MemberConverter.toMyProfile(member);
    }

    /** 값 검증은 @Pattern이 이미 끝냈으므로 여기서는 변환만 한다. */
    private Gender toGender(String gender) {
        return gender == null ? null : Gender.valueOf(gender);
    }
}
