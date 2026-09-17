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
    CALL_PARTICIPANT_NOT_FOUND(HttpStatus.BAD_REQUEST, "CALL4002", "통화 상대를 찾을 수 없습니다."),
    CALL_PARTICIPANT_REQUIRED(HttpStatus.BAD_REQUEST, "CALL4003", "통화 상대가 필요합니다."),
    CALL_INVALID_SIGNALING_PAYLOAD(HttpStatus.BAD_REQUEST, "CALL4004", "통화 시그널링 값이 올바르지 않습니다."),
    CALL_TOO_MANY_PARTICIPANTS(HttpStatus.BAD_REQUEST, "CALL4005", "1:1 통화는 한 명의 상대만 지정할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
