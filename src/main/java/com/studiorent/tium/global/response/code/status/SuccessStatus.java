package com.studiorent.tium.global.response.code.status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
@AllArgsConstructor
public enum SuccessStatus {

    // 일반적인 응답
    _OK(HttpStatus.OK, "COMMON200", "성공입니다."),
    _CREATED(HttpStatus.CREATED, "COMMON201", "새로운 리소스가 생성되었습니다."),
    _ACCEPTED(HttpStatus.ACCEPTED, "COMMON202", "요청이 접수되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
