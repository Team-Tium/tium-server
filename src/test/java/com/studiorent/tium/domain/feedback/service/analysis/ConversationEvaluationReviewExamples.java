package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationEvaluationRubric.AssessmentStatus;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationEvaluationRubric.Severity;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 팀 검토용 대화 예시와 기대 판정. 실제 모델 응답이 아니다. */
record ConversationEvaluationReviewExamples(@NotEmpty List<@NotNull @Valid ReviewExample> reviewExamples) {
    /**
     * 사람이 검토할 기대 결과 예시이며, 실제 LLM의 정확성을 검증한 결과는 아니다.
     * 예: example.simple_invitation은 "점심 같이 먹을래?" / "좋아."라는 messages와,
     * 상황 파악 기준은 NOT_APPLICABLE, 선택권 기준은 SATISFIED라는 expectedAssessments를 담는다.
     * note에는 "사전 감정 확인이나 문제 파악이 없다는 이유로 지적하지 않는다"는 검토 의도를 남긴다.
     */
    public record ReviewExample(
            @NotBlank String id,
            @NotBlank String scenario,
            @NotEmpty List<@NotNull @Valid ExampleUtterance> messages,
            @NotNull List<@NotNull @Valid ExpectedAssessment> expectedAssessments,
            @NotBlank String note
    ) {
    }

    /** 예: {"messageId":"e1", "speaker":"ME", "text":"점심 같이 먹을래?"} */
    public record ExampleUtterance(
            @NotBlank String messageId,
            @NotNull ConversationAnalysisInput.Speaker speaker,
            @NotBlank String text
    ) {
    }

    /**
     * 위 예시 대화에서 사람이 기대하는 개별 기준 판정.
     * <pre>
     * {
     *   "criterionId": "purpose.advice.autonomy",
     *   "status": "SATISFIED",
     *   "evidenceMessageIds": ["e1", "e2"],
     *   "severity": null,
     *   "resolved": false,
     *   "reason": "의향을 묻고 상대가 자발적으로 수락했다."
     * }
     * </pre>
     * 위반이 아니므로 severity는 null이다. resolved=false는 미해결 문제라는 뜻이 아니라,
     * 이 예시에 '발생한 문제를 후속 발화로 해소한 사건'이 없다는 뜻이다.
     */
    public record ExpectedAssessment(
            @NotBlank String criterionId, // 예시에서 적용할 고정 기준 ID
            @NotNull AssessmentStatus status, // 사람이 검토할 기대 판정
            @NotEmpty List<@NotBlank String> evidenceMessageIds, // 예시 messages의 근거 발화 ID
            @Nullable Severity severity, // VIOLATED일 때만 MINOR/MAJOR, 나머지는 null
            boolean resolved, // 실제 후속 발화로 문제가 해소됐는지. 비위반이면 false
            @NotBlank String reason // 이 판정을 기대하는 이유
    ) {
    }

}
