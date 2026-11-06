package com.studiorent.tium.domain.feedback.dto.analysis;

import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 관계·발화 목적·맥락을 결합하는 평가 기준 원본.
 * 고정 기준은 JSON에서 제공하고, 맥락별 추가 질문은 근거를 포함하여 평가 단계에서 구성한다.
 *
 * JSON 파일 → 이 객체로 역직렬화 → 사용자 발화의 목적·관계에 맞는 기준 선택 → 평가 프롬프트.
 * 현재 선택 로직은 ConversationEvaluationPreviewExperimentTest.selectPreviewRubric()에 있는 실험 코드다.
 * 아래의 '항상 전달/선택 전달/서버 기록용'은 이 실험 코드의 동작을 설명한다.
 *
 * <p>예시: 친구에게 "점심 같이 먹을래?"라고 말하고 상대가 "좋아."라고 답한 대화.
 * 분석 대상 사용자의 목적 후보가 ADVICE_OR_SUGGESTION이고 입력 관계가 FRIEND라면,
 * 공통 지침, 공통 기준 + 조언/제안 기준(목적) 친구 관계 기준을 더하여 평가
 * 조언 기준을 선택했어도 모든 질문을 반드시 적용하는 것은 아니다.
 * "문제 상황을 파악했는가?"는 가벼운 식사 제안에 NOT_APPLICABLE이고,
 * "선택권을 존중했는가?"는 의향을 묻고 자발적으로 수락한 근거로 SATISFIED가 될 수 있다.
 * 즉, 목적·관계로 후보 기준을 고른 뒤 각 기준의 적용 조건과 대화 맥락을 확인한다.
 *
 * <p>이 객체는 평가 기준을 담는다. 현재 대화의 판정 결과를 저장하는 객체는 아니다.
 * 검토용 예시와 기대 판정은 테스트 리소스 conversationEvaluationReviewExamples.json에 별도로 둔다.
 */
public record ConversationEvaluationRubric(
        @NotBlank String version, // 서버 기록용: 어떤 버전의 기준을 사용했는지 식별한다.

        // 항상 전달: 대상·범위 준수, 분석 검토, OTHER/UNKNOWN 처리 등 공통 원칙.
        @NotEmpty List<@NotNull @Valid Guideline> commonGuidelines,

        // 항상 전달: 개별 기준을 충족/위반/비적용/정보 부족 중 무엇으로 판정할지 정의한다.
        @NotEmpty List<@NotNull @Valid AssessmentStatusDefinition> assessmentStatuses,

        // 항상 전달: 심각도 부여 조건, 같은 사건 중복 집계 방지, 문제 해소 처리 방식.
        @NotEmpty List<@NotBlank String> assessmentInstructions,

        // 항상 전달: 발화 목적과 무관하게 확인할 모욕·위협·명시적 경계 침해 등의 기준.
        @NotEmpty List<@NotNull @Valid Criterion> commonCriteria,

        // 선택 전달: 사용자 발화에서 분석된 목적에 해당하는 그룹의 criteria만 가져온다.
        // 예: ADVICE_OR_SUGGESTION이면 조언 기준 선택. 여러 목적이면 해당 기준들을 모은다.
        @NotEmpty List<@NotNull @Valid PurposeCriteria> purposeCriteria,

        // 선택 전달: 입력 관계가 FRIEND이면 친구 기준, STRANGER이면 초면 기준만 가져온다.
        @NotEmpty List<@NotNull @Valid RelationshipCriteria> relationshipCriteria,

        // 항상 전달: 기존 기준을 맥락에 맞게 구체화하는 추가 질문 생성 규칙.
        // 추가 질문에는 baseCriterionId와 근거 발화가 필요하며 새로운 의무를 만들어 감점할 수 없다.
        @NotEmpty List<@NotBlank String> contextEvaluationInstructions,

        // 항상 전달: 개별 문제의 MINOR/MAJOR와 전체 GOOD/AMBIGUOUS/BAD를 정하는 정책.
        @NotNull @Valid GradingPolicy gradingPolicy
) {
    /**
     * 채점 질문이 아니라 평가자가 따라야 할 행동 지침이다.
     * <pre>
     * {"id": "common.target",
     *  "instruction": "분석 대상 사용자의 발화를 평가하고 상대 발화는 맥락으로 사용한다."}
     * </pre>
     * 예: ME가 평가 대상이면 OTHER의 거친 표현을 ME의 잘못으로 평가하지 않는다.
     * JSON 예시는 필드 구조를 설명하기 위해 원본 문구를 줄여 쓴 것이다.
     */
    public record Guideline(
            @NotBlank String id, // 지침의 고유 식별자
            @NotBlank String instruction // LLM이 따라야 할 지시
    ) {
    }

    /**
     * 실제로 적용 여부와 충족 여부를 확인할 평가 질문 하나.
     * <pre>
     * {
     *   "id": "purpose.advice.understanding",
     *   "question": "문제 해결을 위한 조언이 상대의 상황을 고려했는가?",
     *   "applicability": "문제 해결을 위한 조언에 적용한다.",
     *   "violationConditions": ["상대가 명시한 제약을 무시하고 결정을 압박한 경우."],
     *   "exclusions": ["점심·산책 등 가벼운 공동 활동 제안."]
     * }
     * </pre>
     * "점심 같이 먹을래?"는 exclusions에 해당하므로 이 질문은 NOT_APPLICABLE이다.
     * 질문 목록에 들어 있다는 이유만으로 적용하거나 위반으로 판정하면 안 된다.
     * JSON 예시의 문구는 축약했으며 실제 기준은 원본 JSON을 사용한다.
     */
    public record Criterion(
            @NotBlank String id, // 평가 결과에서 어떤 기준을 적용했는지 추적하는 ID
            @NotBlank String question, // 무엇을 확인할지: 예) 상대의 선택권을 존중했는가?
            @NotBlank String applicability, // 어떤 대화에 이 기준을 적용할지
            @NotEmpty List<@NotBlank String> violationConditions, // VIOLATED로 판단할 실제 근거 조건
            @NotEmpty List<@NotBlank String> exclusions // 위반으로 오해하면 안 되는 예외
    ) {
    }

    /** 예: purpose가 ADVICE_OR_SUGGESTION인 그룹에는 상황 파악·실행 가능성·선택권 기준이 있다. */
    public record PurposeCriteria(
            @NotNull UtterancePurpose purpose, // 필터 키: 분석 결과의 목적과 비교한다.
            @NotEmpty List<@NotNull @Valid Criterion> criteria // 이 목적에 맞는 평가 질문 목록
    ) {
    }

    /** 예: FRIEND 그룹에는 친구가 표현한 감정 존중과 관계 맥락에 맞는 표현 기준이 있다. */
    public record RelationshipCriteria(
            @NotNull RelationShip relationship, // 필터 키: 서버가 지정한 대화 관계와 비교한다.
            @NotEmpty List<@NotNull @Valid Criterion> criteria // 해당 관계에서 추가로 확인할 질문 목록
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

    /** 예: status=NOT_APPLICABLE, meaning="현재 대화에 적용할 조건을 만족하지 않는다." */
    public record AssessmentStatusDefinition(
            @NotNull AssessmentStatus status,
            @NotBlank String meaning
    ) {
    }

    /**
     * 개별 위반의 심각도와 전체 등급을 구분하는 정책 문구.
     * 예: 명시적 협박이 확인되면 MAJOR에 해당하여 전체 BAD로 평가할 수 있다.
     * NOT_APPLICABLE 기준이 있다는 이유로 AMBIGUOUS가 되지는 않는다.
     * 이 record 자체가 등급을 계산하지는 않으며, 판단 정책을 평가 프롬프트에 전달한다.
     */
    public record GradingPolicy(
            @NotBlank String minor, // 어떤 위반을 경미한 문제로 볼지
            @NotBlank String major, // 어떤 위반을 중대한 문제로 볼지
            @NotBlank String good, // 적절한 반응 근거가 있고 미해결 문제가 없는 경우
            @NotBlank String ambiguous, // 미해결 MINOR 또는 전체 판단을 막는 핵심 정보 부족
            @NotBlank String bad // MAJOR가 확인된 경우
    ) {
    }
}
