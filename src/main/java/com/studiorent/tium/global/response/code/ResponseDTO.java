package com.studiorent.tium.global.response.code;


import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@Builder
public class ResponseDTO {

    private HttpStatus httpStatus;
    private final boolean isSuccess;
    private final String code;
    private final String message;
}
