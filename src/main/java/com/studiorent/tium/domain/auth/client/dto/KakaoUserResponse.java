package com.studiorent.tium.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public record KakaoAccount(String email) {
    }

    public String email() {
        return kakaoAccount == null ? null : kakaoAccount.email();
    }
}
