package com.studiorent.tium.domain.auth.client.dto;

public record NaverUserResponse(
        Response response
) {

    public record Response(String id, String email) {
    }

    public String id() {
        return response == null ? null : response.id();
    }

    public String email() {
        return response == null ? null : response.email();
    }
}
