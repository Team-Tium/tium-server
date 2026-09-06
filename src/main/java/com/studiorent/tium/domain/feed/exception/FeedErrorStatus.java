package com.studiorent.tium.domain.feed.exception;

import com.studiorent.tium.global.response.code.BaseCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum FeedErrorStatus implements BaseCode {

    FEED_NOT_FOUND(HttpStatus.NOT_FOUND, "FEED4041", "존재하지 않는 피드입니다."),
    FEED_FORBIDDEN(HttpStatus.FORBIDDEN, "FEED4031", "피드에 대한 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
