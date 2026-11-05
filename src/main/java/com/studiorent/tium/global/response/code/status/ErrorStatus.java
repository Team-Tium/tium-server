package com.studiorent.tium.global.response.code.status;


import com.studiorent.tium.global.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseCode {

    // Common Error
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "FEED4001", "유효하지 않은 피드 커서입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러입니다. 관리자에게 문의하세요."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "찾을 수 없는 요청입니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "COMMON409", "동일한 요청이 처리 중이거나 방금 처리되었습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "COMMON410", "동일한 Idempotency-Key로 다른 요청이 감지되었습니다."),

    // Auth Error - 소셜 로그인
    AUTH_INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4001", "유효하지 않은 소셜 토큰입니다."),
    AUTH_SOCIAL_CREDENTIAL_REQUIRED(HttpStatus.BAD_REQUEST, "AUTH4002", "token과 authorizationCode 중 하나는 필수입니다."),
    AUTH_UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH4003", "지원하지 않는 소셜 제공자입니다."),

    // Auth Error - 서비스 토큰
    AUTH_INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4011", "유효하지 않은 액세스 토큰입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4012", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4013", "만료된 리프레시 토큰입니다."),

    // Auth Error - 회원
    AUTH_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH4041", "존재하지 않는 회원입니다."),

    // Auth Error - 외부 연동
    AUTH_SOCIAL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH5001", "소셜 서버와의 통신에 실패했습니다."),

    // Member Error - 회원
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER4041", "존재하지 않는 회원입니다."),
    MEMBER_WITHDRAWN(HttpStatus.NOT_FOUND, "MEMBER4042", "탈퇴한 회원입니다."),

    // Chat Error - 요청 값
    CHAT_EMPTY_CONTENT(HttpStatus.BAD_REQUEST, "CHAT4001", "메시지 내용은 비어있을 수 없습니다."),
    CHAT_SELF_ROOM_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CHAT4002", "자기 자신과는 채팅방을 만들 수 없습니다."),
    CHAT_UNSUPPORTED_MESSAGE_TYPE(HttpStatus.BAD_REQUEST, "CHAT4003", "지원하지 않는 메시지 타입입니다."),

    // Chat Error - 권한
    CHAT_NOT_ROOM_MEMBER(HttpStatus.FORBIDDEN, "CHAT4031", "채팅방에 속한 사용자가 아닙니다."),
    CHAT_ALREADY_LEFT(HttpStatus.FORBIDDEN, "CHAT4032", "이미 나간 채팅방입니다."),
    CHAT_OPPONENT_LEFT(HttpStatus.FORBIDDEN, "CHAT4033", "상대방이 나간 채팅방입니다."),
    CHAT_CONVERSATION_EMPTY(HttpStatus.BAD_REQUEST, "CHAT4004", "추천할 대화가 없습니다."),

    // Chat Error - 대상 없음
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT4041", "존재하지 않는 채팅방입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT4042", "존재하지 않는 메시지입니다."),
    CHAT_SUGGESTION_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT5001", "답장 추천 응답 형식이 올바르지 않습니다."),

    // Matching Error
    MATCHING_UNSUPPORTED_TYPE(HttpStatus.BAD_REQUEST, "MATCHING4001", "지원하지 않는 매칭 타입입니다."),
    MATCHING_NOT_WAITING(HttpStatus.NOT_FOUND, "MATCHING4041", "매칭 대기 중이 아닙니다."),
    MATCHING_ALREADY_WAITING(HttpStatus.CONFLICT, "MATCHING4091", "이미 매칭을 기다리는 중입니다."),

    // feedback error - 피드백 없음
    FEEDBACK_CONVERSATION_EMPTY(HttpStatus.BAD_REQUEST, "FEEDBACK4001", "피드백을 생성할 대화가 없습니다."),
    FEEDBACK_AUDIO_EMPTY(HttpStatus.BAD_REQUEST, "FEEDBACK4002", "피드백을 생성할 녹음 파일이 비어있습니다."),
    FEEDBACK_AUDIO_UNSUPPORTED(HttpStatus.BAD_REQUEST, "FEEDBACK4003", "지원하지 않는 녹음 파일 형식입니다."),
    FEEDBACK_NOT_FOUND(HttpStatus.NOT_FOUND, "FEEDBACK404", "피드백이 존재하지 않습니다"),
    FEEDBACK_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5002", "피드백 응답 형식이 올바르지 않습니다."),
    FEEDBACK_CRITERIA_FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,    "FEEDBACK5001","피드백 기준 파일을 읽지 못했습니다." ),
    FEEDBACK_TRANSCRIPTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5003", "녹음 파일을 텍스트로 변환하지 못했습니다."),
    FEEDBACK_ANALYSIS_INVALID_RESPONSE(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5004", "대화 분석 응답이 원본 대화 또는 분석 형식과 일치하지 않습니다."),
    FEEDBACK_ANALYSIS_CONTEXT_EVIDENCE_ID_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5005", "대화 분석의 맥락 근거에 존재하지 않는 발화 ID가 포함되어 있습니다."),
    FEEDBACK_ANALYSIS_DUPLICATE_CONTEXT_EVIDENCE_ID(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5006", "대화 분석의 맥락 근거 발화 ID가 중복되었습니다."),
    FEEDBACK_ANALYSIS_UTTERANCE_COUNT_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5007", "대화 분석의 발화 개수가 원본과 일치하지 않습니다."),
    FEEDBACK_ANALYSIS_UTTERANCE_ORDER_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5008", "대화 분석의 발화 ID 또는 순서가 원본과 일치하지 않습니다."),
    FEEDBACK_ANALYSIS_DUPLICATE_PURPOSE(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5009", "같은 발화의 분석 목적이 중복되었습니다."),
    FEEDBACK_ANALYSIS_PURPOSE_QUOTE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5010", "발화 목적의 근거 인용문이 해당 원문에 존재하지 않습니다."),
    FEEDBACK_ANALYSIS_INVALID_UNKNOWN_USAGE(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5011", "UNKNOWN 목적은 단독으로 사용하고 불확실성 설명을 포함해야 합니다."),
    FEEDBACK_ANALYSIS_EMOTION_QUOTE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5012", "감정 인용문이 해당 발화 원문에 존재하지 않습니다."),
    FEEDBACK_ANALYSIS_REQUEST_QUOTE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "FEEDBACK5013", "요청 인용문이 해당 발화 원문에 존재하지 않습니다."),
    FEEDBACK_ANALYSIS_API_AUTH_FAILED(HttpStatus.BAD_GATEWAY, "FEEDBACK5014", "대화 분석 API 인증에 실패했습니다."),
    FEEDBACK_ANALYSIS_API_PERMISSION_DENIED(HttpStatus.BAD_GATEWAY, "FEEDBACK5015", "대화 분석 API 접근 권한이 없습니다."),
    FEEDBACK_ANALYSIS_MODEL_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "FEEDBACK5016", "설정된 대화 분석 모델을 사용할 수 없습니다."),
    FEEDBACK_ANALYSIS_API_RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "FEEDBACK5017", "대화 분석 API 호출이 일시적으로 제한되었습니다."),
    FEEDBACK_ANALYSIS_API_QUOTA_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "FEEDBACK5018", "대화 분석 API 사용량 한도를 초과했습니다."),
    FEEDBACK_ANALYSIS_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "FEEDBACK5019", "대화 분석 API 응답 시간이 초과되었습니다."),
    FEEDBACK_ANALYSIS_API_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "FEEDBACK5020", "대화 분석 API에 연결하지 못했습니다."),
    FEEDBACK_ANALYSIS_API_FAILED(HttpStatus.BAD_GATEWAY, "FEEDBACK5021", "대화 분석 API 요청 처리에 실패했습니다."),
    FEEDBACK_ANALYSIS_RESPONSE_CONVERSION_FAILED(HttpStatus.BAD_GATEWAY, "FEEDBACK5022", "대화 분석 응답을 지정된 JSON 구조로 변환하지 못했습니다."),

    // Call STT Error
    CALL_STT_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL_STT4041", "통화 STT 내역이 존재하지 않습니다."),
    CALL_STT_NOT_READY(HttpStatus.BAD_REQUEST, "CALL_STT4001", "상대방 통화 STT 내역이 아직 준비되지 않았습니다."),
    CALL_STT_FILE_PART_REQUIRED(HttpStatus.BAD_REQUEST, "CALL_STT4002", "file 파트는 필수입니다."),
    CALL_STT_MULTIPART_REQUIRED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CALL_STT4151", "multipart/form-data 형식으로 요청해야 합니다."),
    CALL_STT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "CALL_STT4131", "업로드 가능한 녹음 파일 크기를 초과했습니다."),
    CALL_STT_ALREADY_USED(HttpStatus.CONFLICT, "CALL_STT4091", "이미 피드백 생성에 사용된 STT 내역은 수정할 수 없습니다."),

    // Dev Error - 개발 전용 API
    DEV_API_FORBIDDEN(HttpStatus.FORBIDDEN, "DEV4031", "개발용 API는 운영 환경에서 사용할 수 없습니다.");



    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    /**
     * 대화 분석 API의 HTTP 상태와 외부 오류 코드를 서비스 오류로 변환한다.
     * 외부 SDK 예외 타입에 의존하지 않도록 필요한 값만 받는다.
     */
    public static ErrorStatus fromAnalysisApiError(int statusCode, String apiErrorCode) {
        return switch (statusCode) {
            case 401 -> FEEDBACK_ANALYSIS_API_AUTH_FAILED;
            case 403 -> FEEDBACK_ANALYSIS_API_PERMISSION_DENIED;
            case 404 -> "model_not_found".equals(apiErrorCode)
                    ? FEEDBACK_ANALYSIS_MODEL_UNAVAILABLE
                    : FEEDBACK_ANALYSIS_API_FAILED;
            case 429 -> "insufficient_quota".equals(apiErrorCode)
                    ? FEEDBACK_ANALYSIS_API_QUOTA_EXCEEDED
                    : FEEDBACK_ANALYSIS_API_RATE_LIMITED;
            case 408, 504 -> FEEDBACK_ANALYSIS_API_TIMEOUT;
            default -> FEEDBACK_ANALYSIS_API_FAILED;
        };
    }
}
