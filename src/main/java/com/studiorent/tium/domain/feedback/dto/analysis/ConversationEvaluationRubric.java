package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * 관계·발화 목적·맥락을 결합하는 평가 기준 원본.
 * 고정 기준은 JSON에서 제공하고, 맥락별 추가 질문은 근거를 포함하여 평가 단계에서 구성한다.
 *
 * JSON 파일 → 이 객체로 역직렬화 → 사용자 발화의 목적·관계에 맞는 기준 선택 → 평가 프롬프트.
 * 현재 선택 로직은 ConversationAnalysisLiveApiTest.selectPreviewRubric()에 있는 실험 코드다.
 * 아래의 '항상 전달/선택 전달/전달 안 함'은 이 실험 코드의 동작을 설명한다.
 */
public record ConversationEvaluationRubric(
        @NotBlank String version, // 항상 전달: 어떤 버전의 기준을 사용했는지 식별한다.
        @NotBlank String source, // 항상 전달: 기준의 작성 근거. 학술적으로 검증됐다는 뜻은 아니다.

        // 항상 전달: 원문 근거 제시, 평가 대상과 범위 준수 등 모든 평가의 공통 원칙.
        @NotEmpty List<@NotNull @Valid Guideline> commonGuidelines,

        // 항상 전달: 첫 번째 LLM의 분석 오류나 정보 부족을 사용자 잘못으로 채점하지 않는 규칙.
        @NotEmpty List<@NotNull @Valid Guideline> analysisReviewGuidelines,

        // 항상 전달: 개별 기준을 충족/위반/비적용/정보 부족 중 무엇으로 판정할지 정의한다.
        @NotEmpty List<@NotNull @Valid AssessmentStatusDefinition> assessmentStatuses,

        // 항상 전달: 심각도 부여 조건, 같은 사건 중복 집계 방지, 문제 해소 처리 방식.
        @NotEmpty List<@NotBlank String> assessmentInstructions,

        // 항상 전달: 발화 목적과 무관하게 확인할 모욕·위협·명시적 경계 침해 등의 기준.
        @NotEmpty List<@NotNull @Valid Criterion> commonCriteria,

        // 선택 전달: 사용자 발화에서 분석된 목적에 해당하는 그룹의 criteria만 가져온다.
        // 예: ADVICE_OR_SUGGESTION이면 조언 기준 선택. 여러 목적이면 해당 기준들을 모은다.
        @NotEmpty List<@NotNull @Valid PurposeCriteria> purposeCriteria,

        // 선택 전달: OTHER 또는 UNKNOWN이 분석된 경우 해당 목적의 처리 지침을 가져온다.
        // 목적 분류가 어렵다는 사실 자체를 사용자 대화의 문제로 평가하지 않도록 한다.
        @NotEmpty List<@NotNull @Valid PurposeHandling> purposeHandling,

        // 선택 전달: 입력 관계가 FRIEND이면 친구 기준, STRANGER이면 초면 기준만 가져온다.
        @NotEmpty List<@NotNull @Valid RelationshipCriteria> relationshipCriteria,

        // 항상 전달: 기존 기준을 맥락에 맞게 구체화하는 추가 질문 생성 규칙.
        // 추가 질문에는 baseCriterionId와 근거 발화가 필요하며 새로운 의무를 만들어 감점할 수 없다.
        @NotEmpty List<@NotBlank String> contextEvaluationInstructions,

        // 항상 전달: 개별 문제의 MINOR/MAJOR와 전체 GOOD/AMBIGUOUS/BAD를 정하는 정책.
        @NotNull @Valid GradingPolicy gradingPolicy,

        // 모델에 전달하지 않음: 사람이 검토할 반례와 기대 판정. 정답을 주입하지 않고 테스트한다.
        @NotEmpty List<@NotNull @Valid ReviewExample> reviewExamples
) {
    /** 채점 질문이 아니라 평가자가 따라야 할 행동 지침이다. */
    public record Guideline(
            @NotBlank String id, // 지침의 고유 식별자
            @NotBlank String instruction // LLM이 따라야 할 지시
    ) {
    }

    /** 실제로 적용 여부와 충족 여부를 확인할 평가 질문 하나. */
    public record Criterion(
            @NotBlank String id, // 평가 결과에서 어떤 기준을 적용했는지 추적하는 ID
            @NotBlank String question, // 무엇을 확인할지: 예) 상대의 선택권을 존중했는가?
            @NotBlank String applicability, // 어떤 대화에 이 기준을 적용할지
            @NotEmpty List<@NotBlank String> violationConditions, // VIOLATED로 판단할 실제 근거 조건
            @NotEmpty List<@NotBlank String> exclusions // 위반으로 오해하면 안 되는 예외
    ) {
    }

    public record PurposeCriteria(
            @NotNull UtterancePurpose purpose, // 필터 키: 분석 결과의 목적과 비교한다.
            @NotEmpty List<@NotNull @Valid Criterion> criteria // 이 목적에 맞는 평가 질문 목록
    ) {
    }

    public record RelationshipCriteria(
            @NotNull RelationShip relationship, // 필터 키: 서버가 지정한 대화 관계와 비교한다.
            @NotEmpty List<@NotNull @Valid Criterion> criteria // 해당 관계에서 추가로 확인할 질문 목록
    ) {
    }

    /** 목적을 분류하기 어려운 경우는 사용자 문제가 아니라 평가 가능 여부로 다룬다. */
    public record PurposeHandling(
            @NotNull UtterancePurpose purpose,
            @NotEmpty List<@NotBlank String> instructions
    ) {
    }

    public enum AssessmentStatus {
        SATISFIED, // 적용 가능한 기준을 충족한 근거가 있음
        VIOLATED, // 적용 가능한 기준의 위반 근거가 있음
        NOT_APPLICABLE, // 현재 대화에는 적용할 기준이 아님
        INSUFFICIENT_CONTEXT // 판단할 정보가 부족함. 위반과 구분한다.
    }

    public enum Severity {
        MINOR, // 근거가 확인된 개선 필요 문제
        MAJOR // 근거가 확인된 중대한 모욕·위협·강요 등
    }

    public record AssessmentStatusDefinition(
            @NotNull AssessmentStatus status,
            @NotBlank String meaning
    ) {
    }

    /** 사람이 검토할 기대 결과 예시이며, 실제 LLM의 정확성을 검증한 결과는 아니다. */
    public record ReviewExample(
            @NotBlank String id,
            @NotBlank String scenario,
            @NotEmpty List<@NotNull @Valid ExampleUtterance> messages,
            @NotNull List<@NotNull @Valid ExpectedAssessment> expectedAssessments,
            @NotBlank String note
    ) {
    }

    public record ExampleUtterance(
            @NotBlank String messageId,
            @NotNull ConversationAnalysisInput.Speaker speaker,
            @NotBlank String text
    ) {
    }

    public record ExpectedAssessment(
            @NotBlank String criterionId, // 예시에서 적용할 고정 기준 ID
            @NotNull AssessmentStatus status, // 사람이 검토할 기대 판정
            @NotEmpty List<@NotBlank String> evidenceMessageIds, // 예시 messages의 근거 발화 ID
            @Nullable Severity severity, // VIOLATED일 때만 MINOR/MAJOR, 나머지는 null
            boolean resolved, // 실제 후속 발화로 문제가 해소됐는지. 비위반이면 false
            @NotBlank String reason // 이 판정을 기대하는 이유
    ) {
    }

    public record GradingPolicy(
            @NotBlank String minor, // 어떤 위반을 경미한 문제로 볼지
            @NotBlank String major, // 어떤 위반을 중대한 문제로 볼지
            @NotBlank String good, // 적절한 반응 근거가 있고 미해결 문제가 없는 경우
            @NotBlank String ambiguous, // 미해결 MINOR 또는 전체 판단을 막는 핵심 정보 부족
            @NotBlank String bad // MAJOR가 확인된 경우
    ) {
    }
}
