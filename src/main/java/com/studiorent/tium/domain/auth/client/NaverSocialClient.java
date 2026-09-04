package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.domain.auth.client.dto.NaverUserResponse;
import com.studiorent.tium.domain.auth.client.dto.OAuthTokenResponse;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NaverSocialClient implements SocialClient {

    private final SocialApiCaller apiCaller;
    private final NaverProperties properties;

    @Override
    public Provider getProvider() {
        return Provider.NAVER;
    }

    @Override
    public SocialUserInfo fetchUserInfo(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        OAuthTokenResponse token = apiCaller.postForm(properties.tokenUri(), form, OAuthTokenResponse.class);

        if (token == null || !StringUtils.hasText(token.accessToken())) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
        }

        NaverUserResponse user = apiCaller.getWithBearer(
                properties.userInfoUri(), token.accessToken(), NaverUserResponse.class);

        if (user == null || !StringUtils.hasText(user.id())) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
        }
        return new SocialUserInfo(user.id(), user.email());
    }
}
