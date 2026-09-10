package com.studiorent.tium.domain.member.entity;

import com.studiorent.tium.domain.member.entity.enums.Gender;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_provider",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== 소셜 인증 =====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    /** provider가 발급한 소셜 유저 식별자. provider와 묶여 유니크하다. */
    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    /** 소셜 동의 항목이라 제공되지 않을 수 있어 nullable이다. 로그인 식별에는 쓰지 않는다. */
    @Column(length = 255)
    private String email;

    // ===== 온보딩 =====

    /** 온보딩(추가정보 + 자기소개서) 완료 여부. 로그인 응답의 화면 분기 기준이다. */
    @Builder.Default
    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(length = 20)
    private String name;

    /** 본인인증(PASS 등) 없이 값만 저장한다. 인증 연동은 범위 밖이다. */
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** 자기소개서. 길이 제한이 정해지지 않아 TEXT로 둔다. */
    @Column(columnDefinition = "TEXT")
    private String introduction;

    // ===== 탈퇴 =====

    /** 소프트 삭제. null이면 활성 회원이다. */
    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    // ===== 상태 변경 =====

    /**
     * 온보딩 정보를 저장하고 완료 처리한다.
     *
     * null인 값은 기존 값을 그대로 둔다(부분 업데이트).
     * 온보딩 저장 API를 프로필 수정에도 재사용하기 때문에 전량 덮어쓰기로 두지 않는다.
     * 같은 이유로 이 메서드로는 이미 채워진 값을 null로 되돌릴 수 없다.
     */
    public void updateProfile(String name, String phoneNumber, String email, String address,
                              Gender gender, LocalDate birthDate, String introduction) {
        if (name != null) {
            this.name = name;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (email != null) {
            this.email = email;
        }
        if (address != null) {
            this.address = address;
        }
        if (gender != null) {
            this.gender = gender;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (introduction != null) {
            this.introduction = introduction;
        }
        this.onboardingCompleted = true;
    }

    /**
     * 탈퇴 처리. 이미 탈퇴한 회원이면 최초 탈퇴 시각을 유지한다(멱등).
     *
     * providerId를 UUID로 덮어써 소셜 식별자를 파기한다.
     * (provider, provider_id) 유니크 제약을 유지한 채 같은 소셜 계정의 재가입을 허용하기 위해서다.
     * 덮어쓴 뒤에는 원래 계정을 되짚을 수 없으므로 재가입 이력은 추적하지 않는다.
     */
    public void withdraw() {
        if (this.withdrawnAt == null) {
            this.withdrawnAt = LocalDateTime.now();
            this.providerId = UUID.randomUUID().toString();
        }
    }

    public boolean isWithdrawn() {
        return this.withdrawnAt != null;
    }
}
