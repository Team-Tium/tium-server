package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.core.RequestOptions;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpRequestBody;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundedOpenAiHttpClientTest {
    private final HttpClient delegate = mock(HttpClient.class);
    private final HttpRequest request = mock(HttpRequest.class);
    private final AtomicLong nanos = new AtomicLong();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);
    private final List<Duration> sleeps = new ArrayList<>();
    private final List<BoundedOpenAiHttpClient> clients = new ArrayList<>();

    private BoundedOpenAiHttpClient client(Duration budget, int retries) {
        var client = new BoundedOpenAiHttpClient(delegate, budget, retries, Duration.ofMillis(500),
                nanos::get, clock, delay -> { sleeps.add(delay); nanos.addAndGet(delay.toNanos()); });
        clients.add(client);
        return client;
    }

    private TestResponse response(int status) { return response(status, Headers.builder().build(), "{}"); }
    private TestResponse response(int status, Headers headers, String body) { return new TestResponse(status, headers, body); }
    private HttpResponse execute(BoundedOpenAiHttpClient client) { return client.execute(request, RequestOptions.none()); }

    @AfterEach void close() { Thread.interrupted(); clients.forEach(BoundedOpenAiHttpClient::close); }

    @Test void 첫_호출이_성공하면_대기나_추가_호출이_없다() {
        var success = response(200);
        when(delegate.execute(any(), any())).thenReturn(success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        verify(delegate).execute(same(request), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void 일시_오류는_짧게_한번만_재시도하고_실패_응답을_닫는다() {
        var failed = response(503);
        var success = response(200);
        when(delegate.execute(any(), any())).thenReturn(failed, success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        verify(delegate, times(2)).execute(same(request), any());
        assertThat(failed.closed).isTrue();
        assertThat(success.closed).isFalse();
        assertThat(sleeps).containsExactly(Duration.ofMillis(200));
    }

    @Test void 두번째도_실패하면_더_재시도하지_않는다() {
        var first = response(503);
        var last = response(503);
        when(delegate.execute(any(), any())).thenReturn(first, last);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(last);
        verify(delegate, times(2)).execute(any(), any());
        assertThat(first.closed).isTrue();
        assertThat(last.closed).isFalse();
    }

    @ParameterizedTest @ValueSource(ints = {400, 401, 403, 404, 422})
    void 재시도_힌트가_있어도_인증과_잘못된_요청은_반복하지_않는다(int status) {
        var failure = response(status, Headers.builder().put("X-Should-Retry", "true").build(), "{}");
        when(delegate.execute(any(), any())).thenReturn(failure);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(failure);
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void 할당량_부족_429는_재시도하지_않고_본문을_그대로_반환한다() throws IOException {
        String body = "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"quota\"}}";
        var failure = response(429, Headers.builder().build(), body);
        when(delegate.execute(any(), any())).thenReturn(failure);
        var actual = execute(client(Duration.ofSeconds(1), 1));
        assertThat(new String(actual.body().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
        actual.close();
        assertThat(failure.closed).isTrue();
    }

    @Test void 일시_요청_한도_429는_재시도한다() {
        var failure = response(429, Headers.builder().build(), "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
        var success = response(200);
        when(delegate.execute(any(), any())).thenReturn(failure, success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        verify(delegate, times(2)).execute(any(), any());
        assertThat(failure.closed).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"billing_hard_limit_reached", "invalid_api_key", "unknown_error"})
    void 과금이나_영구_오류와_분류되지_않은_429는_재시도하지_않는다(String code) throws IOException {
        String body = "{\"error\":{\"code\":\"" + code + "\"}}";
        when(delegate.execute(any(), any())).thenReturn(response(429, Headers.builder().build(), body));
        var actual = execute(client(Duration.ofSeconds(1), 1));
        assertThat(new String(actual.body().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void 큰_429_본문도_잘라내지_않고_그대로_반환한다() throws IOException {
        String body = " ".repeat(70_000) + "{\"error\":{\"code\":\"insufficient_quota\"}}";
        when(delegate.execute(any(), any())).thenReturn(response(429, Headers.builder().build(), body));
        var actual = execute(client(Duration.ofSeconds(1), 1));
        assertThat(new String(actual.body().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        verify(delegate).execute(any(), any());
    }

    @Test void 긴_RetryAfter를_앞당겨_재시도하지_않는다() {
        var failure = response(503, Headers.builder().put("Retry-After", "60").build(), "{}");
        when(delegate.execute(any(), any())).thenReturn(failure);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(failure);
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void RetryAfter_날짜가_긴_대기를_요구하면_재시도하지_않는다() {
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(clock.instant().plusSeconds(10).atZone(ZoneOffset.UTC));
        var failure = response(503, Headers.builder().put("Retry-After", date).build(), "{}");
        when(delegate.execute(any(), any())).thenReturn(failure);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(failure);
        verify(delegate).execute(any(), any());
    }

    @Test void 짧은_RetryAfterMs는_그대로_기다리고_남은_시간만_전송에_할당한다() {
        var failure = response(503, Headers.builder().put("Retry-After-Ms", "100").build(), "{}");
        var success = response(200);
        when(delegate.execute(any(), any())).thenAnswer(invocation -> {
            nanos.addAndGet(Duration.ofMillis(600).toNanos());
            return failure;
        }).thenReturn(success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(delegate, times(2)).execute(any(), options.capture());
        assertThat(options.getAllValues().get(0).getTimeout().request()).isEqualTo(Duration.ofSeconds(1));
        assertThat(options.getAllValues().get(1).getTimeout().request()).isEqualTo(Duration.ofMillis(300));
        assertThat(sleeps).containsExactly(Duration.ofMillis(100));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "invalid", "NaN"})
    void 잘못되거나_음수인_RetryAfter는_짧은_기본_대기를_사용한다(String header) {
        when(delegate.execute(any(), any())).thenReturn(
                response(503, Headers.builder().put("Retry-After", header).build(), "{}"), response(200));
        execute(client(Duration.ofSeconds(1), 1));
        assertThat(sleeps).containsExactly(Duration.ofMillis(200));
    }

    @Test void 첫_요청에서_시간을_소진하면_추가_대기나_호출이_없다() {
        var failure = response(503);
        when(delegate.execute(any(), any())).thenAnswer(invocation -> {
            nanos.set(Duration.ofSeconds(1).toNanos()); return failure;
        });
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(failure);
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void 연결_오류도_예산_안에서_한번만_재시도한다() {
        var success = response(200);
        when(delegate.execute(any(), any())).thenThrow(new OpenAIIoException("test", new IOException())).thenReturn(success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        verify(delegate, times(2)).execute(any(), any());
    }

    @Test void 소켓_타임아웃은_취소로_오해하지_않고_남은_예산에서_재시도한다() {
        var success = response(200);
        when(delegate.execute(any(), any())).thenThrow(new OpenAIIoException("test", new SocketTimeoutException())).thenReturn(success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        verify(delegate, times(2)).execute(any(), any());
    }

    @Test void 요청_타임아웃의_일반_InterruptedIOException은_취소로_오해하지_않는다() {
        var success = response(200);
        when(delegate.execute(any(), any()))
                .thenThrow(new OpenAIIoException("test", new InterruptedIOException("timeout")))
                .thenReturn(success);
        assertThat(execute(client(Duration.ofSeconds(1), 1))).isSameAs(success);
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        verify(delegate, times(2)).execute(any(), any());
    }

    @Test void 실제_인터럽트_원인이_있으면_플래그가_지워졌어도_복원하고_재시도하지_않는다() {
        var failure = new OpenAIIoException("test", new InterruptedException());
        when(delegate.execute(any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> execute(client(Duration.ofSeconds(1), 1))).isSameAs(failure);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verify(delegate).execute(any(), any());
        assertThat(sleeps).isEmpty();
    }

    @Test void 서버가_재시도를_금지하면_반복하지_않는다() {
        when(delegate.execute(any(), any())).thenReturn(response(503, Headers.builder().put("X-Should-Retry", "false").build(), "{}"));
        execute(client(Duration.ofSeconds(1), 1));
        verify(delegate).execute(any(), any());
    }

    @Test void 반복할_수_없는_요청_본문은_재전송하지_않는다() {
        var body = mock(HttpRequestBody.class);
        when(request.body()).thenReturn(body);
        when(body.repeatable()).thenReturn(false);
        when(delegate.execute(any(), any())).thenReturn(response(503));
        execute(client(Duration.ofSeconds(1), 1));
        verify(delegate).execute(any(), any());
    }

    @Test void 인터럽트된_요청은_전송하지_않고_플래그를_유지한다() {
        var client = client(Duration.ofSeconds(1), 1);
        Thread.currentThread().interrupt();
        assertThatThrownBy(() -> execute(client)).isInstanceOf(OpenAIIoException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        verifyNoInteractions(delegate);
    }

    @Test void 대기_중_인터럽트되면_추가_전송을_하지_않는다() {
        var client = new BoundedOpenAiHttpClient(delegate, Duration.ofSeconds(1), 1, Duration.ofMillis(500),
                nanos::get, clock, delay -> { throw new InterruptedException(); });
        clients.add(client);
        var failure = response(503);
        when(delegate.execute(any(), any())).thenReturn(failure);
        assertThatThrownBy(() -> execute(client)).isInstanceOf(OpenAIIoException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(failure.closed).isTrue();
        verify(delegate).execute(any(), any());
    }

    @Test void 비동기_호출도_같은_재시도_제한을_적용한다() throws Exception {
        var success = response(200);
        when(delegate.execute(any(), any())).thenReturn(response(503), success);
        assertThat(client(Duration.ofSeconds(1), 1).executeAsync(request, RequestOptions.none()).get(1, TimeUnit.SECONDS)).isSameAs(success);
        verify(delegate, times(2)).execute(any(), any());
    }

    @Test void 비동기_취소_직후_도착한_응답도_닫는다() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var success = response(200);
        when(delegate.execute(any(), any())).thenAnswer(invocation -> {
            started.countDown();
            try { release.await(); } catch (InterruptedException ignored) { }
            return success;
        });
        var future = client(Duration.ofSeconds(1), 1).executeAsync(request, RequestOptions.none());
        try {
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(true)).isTrue();
        } finally { release.countDown(); }
        assertThat(success.closedSignal.await(2, TimeUnit.SECONDS)).isTrue();
        verify(delegate).execute(any(), any());
    }

    private static final class TestResponse implements HttpResponse {
        private final int status;
        private final Headers headers;
        private final InputStream body;
        private final CountDownLatch closedSignal = new CountDownLatch(1);
        private boolean closed;
        private TestResponse(int status, Headers headers, String body) {
            this.status = status; this.headers = headers;
            this.body = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
        @Override public int statusCode() { return status; }
        @Override public Headers headers() { return headers; }
        @Override public InputStream body() { return body; }
        @Override public void close() { closed = true; closedSignal.countDown(); }
    }
}
