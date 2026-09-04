package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.domain.member.entity.enums.Provider;

public interface SocialClient {

    Provider getProvider();

    SocialUserInfo fetchUserInfo(String authorizationCode);
}
