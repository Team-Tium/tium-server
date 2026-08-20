package com.studiorent.tium.global.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.studiorent.tium.global.response.code.BaseCode;
import com.studiorent.tium.global.response.code.status.SuccessStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {


    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    private final String code;

    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    // 성공한 경우 응답 생성 (기본)
    public static <T> ApiResponse<T> onSuccess(T result){
        return new ApiResponse<>(
                true,
                SuccessStatus._OK.getCode(),
                SuccessStatus._OK.getMessage(),
                result
        );
    }

    // 성공한 경우 응답 생성 (커스텀)
    public static <T> ApiResponse<T> of(BaseCode code, T result){
        return new ApiResponse<>(
                true,
                code.getCode(),
                code.getMessage(),
                result
        );
    }

    // 실패한 경우 응답 생성
    public static <T> ApiResponse<T> onFailure(BaseCode code){
        return new ApiResponse<>(
                false,
                code.getCode(),
                code.getMessage(),
                null
        );
    }

    public static <T> ApiResponse<T> onFailure(BaseCode code, T data){
        return new ApiResponse<>(
                false,
                code.getCode(),
                code.getMessage(),
                data
        );
    }
}
