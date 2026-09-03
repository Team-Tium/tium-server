package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.domain.auth.client.dto.KakaoTokenResponse;
import com.studiorent.tium.domain.auth.client.dto.KakaoUserResponse;
import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoSocialClient implements SocialClient {

    private final RestClient restClient;
    private final KakaoProperties kakaoProperties;

    @Override
    public Provider getProvider() {
        return Provider.KAKAO;
    }

    @Override
    public SocialUserInfo fetchUserInfo(String authorizationCode) {
        String socialAccessToken = exchangeToken(authorizationCode);
        KakaoUserResponse user = fetchUser(socialAccessToken);

        if (user == null || user.id() == null) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
        }
        return new SocialUserInfo(String.valueOf(user.id()), user.email());
    }

    private String exchangeToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoProperties.clientId());
        form.add("redirect_uri", kakaoProperties.redirectUri());
        form.add("code", authorizationCode);

        if (StringUtils.hasText(kakaoProperties.clientSecret())) {
            form.add("client_secret", kakaoProperties.clientSecret());
        }

        KakaoTokenResponse response = call(() -> restClient.post()
                .uri(kakaoProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                    log.warn("카카오 토큰 교환 실패: status={}", res.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                    log.error("카카오 인증 서버 오류: status={}", res.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
                })
                .body(KakaoTokenResponse.class));

        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
        }
        return response.accessToken();
    }

    private KakaoUserResponse fetchUser(String socialAccessToken) {
        return call(() -> restClient.get()
                .uri(kakaoProperties.userInfoUri())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + socialAccessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, res) -> {
                    log.warn("카카오 사용자 조회 실패: status={}", res.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, res) -> {
                    log.error("카카오 API 서버 오류: status={}", res.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
                })
                .body(KakaoUserResponse.class));
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("카카오 서버 연결 실패", e);
            throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
        }
    }
}
