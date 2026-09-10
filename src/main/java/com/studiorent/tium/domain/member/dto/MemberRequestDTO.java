package com.studiorent.tium.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class MemberRequestDTO {

    /**
     * 온보딩 정보 저장 요청.
     *
     * 모든 필드가 선택값이다. 보내지 않은 필드는 기존 값을 그대로 둔다.
     * 온보딩 화면이 실제로 채우는 것은 name / birthDate / address / email / gender 다섯 개이며,
     * 전부 선택으로 둔 것은 이 엔드포인트를 프로필 수정에도 재사용하기 때문이다.
     */
    public record OnboardingProfileDTO(
            @Pattern(regexp = "^[가-힣a-zA-Z]{2,20}$", message = "이름은 2~20자의 한글 또는 영문이어야 합니다.")
            @Schema(description = "이름", example = "김티움")
            String name,

            @PastOrPresent(message = "생년월일은 미래 날짜일 수 없습니다.")
            @Schema(description = "생년월일 (yyyy-MM-dd)", example = "1998-05-12")
            LocalDate birthDate,

            @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다.")
            @Schema(description = "기본주소와 상세주소를 합친 한 문자열",
                    example = "경기도 부천시 원미구 ... 101동 1001호")
            String address,

            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
            @Schema(description = "소셜 로그인 시 받아둔 값이 있으면 여기서 수정할 수 있다.",
                    example = "user@example.com")
            String email,

            /*
             * Gender enum이 아니라 String으로 받는다.
             * enum으로 받으면 잘못된 값이 JSON 역직렬화 단계에서 걸려 @Valid 경로를 타지 않고,
             * 명세가 요구하는 result: { "gender": "..." } 형태의 응답이 나오지 않는다.
             */
            @Pattern(regexp = "^(MALE|FEMALE)$", message = "gender는 MALE 또는 FEMALE이어야 합니다.")
            @Schema(description = "성별", allowableValues = {"MALE", "FEMALE"}, example = "MALE")
            String gender,

            @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
            @Schema(description = "본인인증 없이 값만 저장한다.", example = "010-1234-5678")
            String phoneNumber,

            @Schema(description = "자기소개서 본문. 온보딩 화면에는 입력란이 없고 프로필 수정에서 전달한다.")
            String introduction
    ) {
    }
}
