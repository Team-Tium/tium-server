package com.studiorent.tium.global.config;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.OpenAIIoException;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.studiorent.tium.domain.feedback.service.analysis.FeedbackRequestDeadline;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 SDK와 설정을 사용하되 네트워크 대상은 합성 응답을 주는 loopback 서버뿐이다. */
class FeedbackAiConfigTest {
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private OpenAIClient client;
    private volatile int firstStatus = 200;
    private volatile boolean persistentFailure;
    private volatile String retryAfterMs = "0";
    private volatile String errorCode = "temporary_failure";
    private volatile long responseDelayMs;
    private static final String COMPLETION = """
            {"id":"test-completion","object":"chat.completion","created":1,"model":"test-model",
             "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
            """;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int count = requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if (responseDelayMs > 0) {
                try { Thread.sleep(responseDelayMs); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            int status = count == 1 || persistentFailure ? firstStatus : 200;
            String errorType = switch (errorCode) {
                case "rate_limit_exceeded" -> "requests";
                case "insufficient_quota" -> "insufficient_quota";
                default -> "test_error";
            };
            String body = status == 200 ? COMPLETION : "{\"error\":{\"message\":\"synthetic failure\",\"type\":\"" + errorType + "\",\"code\":\"" + errorCode + "\"}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Retry-After-Ms", retryAfterMs);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            exchange.close();
        });
        server.start();
        var common = new OpenAiCommonProperties();
        common.setApiKey("test-only-key");
        common.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        var chat = new OpenAiChatProperties();
        chat.setMaxRetries(1);
        chat.setTimeout(Duration.ofSeconds(5));
        client = new FeedbackAiConfig().feedbackOpenAiClient(common, chat, Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        Thread.interrupted();
        if (client != null) client.close();
        if (server != null) server.stop(0);
    }

    private void complete() {
        assertThat(client.chat().completions().create(ChatCompletionCreateParams.builder()
                .model("test-model").addUserMessage("synthetic input").build())
                .choices().getFirst().message().content()).contains("ok");
    }

    @Test
    void 첫_호출이_성공하면_HTTP_요청은_한번이다() {
        complete();
        assertThat(requests).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 409, 429, 500, 502, 503, 504})
    void 일시적_HTTP_오류는_성공할_때_한번만_재시도한다(int status) {
        firstStatus = status;
        if (status == 429) errorCode = "rate_limit_exceeded";
        complete();
        assertThat(requests).hasValue(2);
    }

    @Test
    void 계속_실패하면_중첩_재시도없이_총_두번에서_종료한다() {
        firstStatus = 503;
        persistentFailure = true;
        assertThatThrownBy(this::complete).isInstanceOf(OpenAIServiceException.class);
        assertThat(requests).hasValue(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422, 429})
    void 잘못된_요청과_인증_권한_사용량_한도_오류는_재시도하지_않는다(int status) {
        firstStatus = status;
        if (status == 429) errorCode = "insufficient_quota";
        assertThatThrownBy(this::complete).isInstanceOf(OpenAIServiceException.class);
        assertThat(requests).hasValue(1);
    }

    @Test
    void 서버가_긴_대기를_요구하면_원래_HTTP_오류를_반환한다() {
        firstStatus = 503;
        retryAfterMs = "60000";
        assertThatThrownBy(this::complete).isInstanceOf(OpenAIServiceException.class);
        assertThat(requests).hasValue(1);
    }

    @Test
    void 남은_전체_예산이_개별_타임아웃보다_짧으면_실제_HTTP_대기도_그_예산으로_제한한다() {
        complete(); // SDK 초기화 비용과 HTTP 대기 시간 검증을 분리한다.
        requests.set(0);
        responseDelayMs = 800;

        assertThatThrownBy(() -> FeedbackRequestDeadline.within(Duration.ofMillis(250), () -> {
            complete();
            return null;
        })).isInstanceOf(OpenAIIoException.class);

        assertThat(requests).hasValue(1);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
}
