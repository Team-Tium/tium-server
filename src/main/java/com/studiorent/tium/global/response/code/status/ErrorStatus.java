package com.studiorent.tium.global.response.code.status;


import com.studiorent.tium.global.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseCode {

    // Common Error
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

    // Chat Error - 대상 없음
    CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT4041", "존재하지 않는 채팅방입니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT4042", "존재하지 않는 메시지입니다."),

    // Dev Error - 개발 전용 API
    DEV_API_FORBIDDEN(HttpStatus.FORBIDDEN, "DEV4031", "개발용 API는 운영 환경에서 사용할 수 없습니다.");



    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
