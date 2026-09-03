package com.studiorent.tium.domain.auth.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken
) {
}
