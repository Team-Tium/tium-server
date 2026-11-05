package com.studiorent.tium.domain.feedback.dto.analysis;



public enum UtterancePurpose {  // 분석에 사용할 발화 목적, 하나의 발화에 어떤 여러 목적이 생길수있음
    QUESTION,                 // 정보나 답변을 요청함
    INFORMATION_SHARING,      // 사실 또는 자신의 경험을 전달함
    EMOTION_EXPRESSION,        // 자신의 감정을 표현함
    ACKNOWLEDGEMENT,           // 상대의 말을 듣거나 이해했음을 표현함
    OPINION,                  // 자신의 생각이나 판단을 표현함
    ADVICE_OR_SUGGESTION,      // 행동이나 해결책을 제안함
    REFUSAL_OR_BOUNDARY,       // 제안을 거절하거나 허용 범위를 표현함
    OTHER,                      // 다른 나머지
    UNKNOWN                 // 근거 부족으로 목적을 판단 할수없는 경우
}
