package com.studiorent.tium.domain.auth.dto;

public class AuthRequestDTO {

    public record LoginDTO(
            String token,
            String authorizationCode
    ) {
    }
}
