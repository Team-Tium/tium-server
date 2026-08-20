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
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "COMMON410", "동일한 Idempotency-Key로 다른 요청이 감지되었습니다.");



    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
