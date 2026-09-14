package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocialApiCaller {

    private final RestClient restClient;

    public <T> T postForm(String uri, MultiValueMap<String, String> form, Class<T> responseType) {
        return call(() -> restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("소셜 토큰 교환 실패: uri={} status={}", uri, response.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("소셜 인증 서버 오류: uri={} status={}", uri, response.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
                })
                .body(responseType));
    }

    public <T> T getWithBearer(String uri, String accessToken, Class<T> responseType) {
        return call(() -> restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("소셜 사용자 조회 실패: uri={} status={}", uri, response.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_INVALID_SOCIAL_TOKEN);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("소셜 API 서버 오류: uri={} status={}", uri, response.getStatusCode());
                    throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
                })
                .body(responseType));
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("소셜 서버 연결 실패", e);
            throw new BusinessException(ErrorStatus.AUTH_SOCIAL_SERVER_ERROR);
        }
    }
}
