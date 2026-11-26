package com.studiorent.tium.domain.feedback.dto.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 첫 번째 LLM의 분석 출력. 평가 등급이나 개선 피드백은 포함하지 않는다.
 * 근거 ID와 인용문의 원문 일치 여부는 입력과 대조하는 별도 검증이 필요하다.
 */
public record ConversationAnalysisResult( // 대화 분석 결과
        @NotBlank String contextSummary,
        @NotEmpty List<@NotBlank String> contextEvidenceMessageIds, //대화 근거 원본아이디들
        @NotEmpty List<@NotNull @Valid UtteranceAnalysis> utterances, // 발화별 분석
        @NotNull List<@NotBlank String> uncertainties
) {
    public record UtteranceAnalysis(
            @NotBlank String messageId, // 대화의 내용중 어떤 발화를 분석했는지
            @NotEmpty List<@NotNull @Valid PurposeCandidate> purposeCandidates, //그 발화의 목적 후보
            // 원문에 명시된 표현을 그대로 인용한다. 확인되지 않으면 빈 배열이다.
            @NotNull List<@NotBlank String> explicitEmotionQuotes, // 감정을 직접 표현한 원문
            @NotNull List<@NotNull @Valid RequestAnalysis> requests, // 요청 표현과 실제 요청 주체·대상
            @NotNull List<@NotBlank String> uncertainties  // 현재 대화만으로 확정할 수 없는 부분 ex) 힘들다 같은 말을 곧바로 공감을 요청했다로 단정않기 위해서 쓴것임
    ) {
    }

    /** ME/OTHER는 현재 대화의 참여자다. 제삼자나 불명확한 대상을 이 둘에 합치지 않는다. */
    public enum RequestParty { ME, OTHER, THIRD_PARTY, UNKNOWN }

    /** 현재 발화의 요청 방식이다. 과거에 직접 했던 요청의 보고도 REPORTED_OR_THIRD_PARTY다. */
    public enum RequestAddressing { DIRECT, REPORTED_OR_THIRD_PARTY, UNCERTAIN }

    public record RequestAnalysis(
            @NotBlank String evidenceQuote,
            @NotNull RequestParty requester,
            @NotNull RequestParty recipient,
            @NotNull RequestAddressing addressing,
            @NotBlank String requestedAction,
            @NotEmpty List<@NotBlank String> contextEvidenceMessageIds,
            @NotBlank String reason
    ) {
    }

    public record PurposeCandidate(
            @NotNull UtterancePurpose purpose, // 목적
            // 해당 messageId의 원문에서 가져온 연속된 부분 문자열이다.
            @NotBlank String evidenceQuote, // 증거 원문
            @NotBlank String reason // 이유
    ) {
    }
}
