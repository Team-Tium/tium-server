package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.domain.auth.client.dto.GoogleUserResponse;
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
public class GoogleSocialClient implements SocialClient {

    private final SocialApiCaller apiCaller;
    private final GoogleProperties properties;

    @Override
    public Provider getProvider() {
        return Provider.GOOGLE;
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

        GoogleUserResponse user = apiCaller.getWithBearer(
                properties.userInfoUri(), token.accessToken(), GoogleUserResponse.class);

        if (user == null || !StringUtils.hasText(user.sub())) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
        }
        return new SocialUserInfo(user.sub(), user.email());
    }
}
