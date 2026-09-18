package com.studiorent.tium.domain.call.exception;

import com.studiorent.tium.global.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CallErrorStatus implements BaseCode {

    CALL_NOT_FOUND(HttpStatus.NOT_FOUND, "CALL4041", "존재하지 않는 통화입니다."),
    CALL_FORBIDDEN(HttpStatus.FORBIDDEN, "CALL4031", "통화에 대한 권한이 없습니다."),
    CALL_ALREADY_COMPLETED(HttpStatus.BAD_REQUEST, "CALL4001", "이미 종료된 통화입니다."),
    CALL_PARTICIPANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "CALL4002", "통화 상대를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
