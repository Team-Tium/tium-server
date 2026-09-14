package com.studiorent.tium.domain.auth.client;

public record SocialUserInfo(
        String providerId,
        String email
) {
}
