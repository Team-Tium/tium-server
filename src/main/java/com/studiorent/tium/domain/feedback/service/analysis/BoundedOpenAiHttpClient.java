package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.core.RequestOptions;
import com.openai.core.Timeout;
import com.openai.core.http.Headers;
import com.openai.core.http.HttpClient;
import com.openai.core.http.HttpRequest;
import com.openai.core.http.HttpResponse;
import com.openai.errors.OpenAIIoException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.SequenceInputStream;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

/** SDK 내부 재시도를 끈 상태에서 HTTP 시도와 짧은 대기를 하나의 시간 예산으로 제한한다. */
public final class BoundedOpenAiHttpClient implements HttpClient {
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(200);
    private static final int MAX_ERROR_BODY_BYTES = 65_536;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> RATE_LIMIT_TYPES = Set.of("", "rate_limit_exceeded", "rate_limit_error", "tokens", "requests");
    private final HttpClient delegate;
    private final Duration callBudget;
    private final int maxRetries;
    private final Duration maxRetryDelay;
    private final LongSupplier nanoTime;
    private final Clock clock;
    private final RetrySleeper sleeper;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public BoundedOpenAiHttpClient(HttpClient delegate, Duration callBudget, int maxRetries, Duration maxRetryDelay) {
        this(delegate, callBudget, maxRetries, maxRetryDelay, System::nanoTime, Clock.systemUTC(), Thread::sleep);
    }

    BoundedOpenAiHttpClient(HttpClient delegate, Duration callBudget, int maxRetries, Duration maxRetryDelay,
                            LongSupplier nanoTime, Clock clock, RetrySleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate);
        if (callBudget.isNegative() || callBudget.isZero() || maxRetries < 0 || maxRetries > 1
                || maxRetryDelay.isNegative() || maxRetryDelay.compareTo(Duration.ofMillis(500)) > 0) {
            throw new IllegalArgumentException("Positive budget, zero or one retry and at most 500ms delay are required");
        }
        this.callBudget = callBudget;
        this.maxRetries = maxRetries;
        this.maxRetryDelay = maxRetryDelay;
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    @Override
    public HttpResponse execute(HttpRequest request, RequestOptions options) {
        long start = nanoTime.getAsLong();
        return execute(request, options, start, budget(options).toNanos());
    }

    private HttpResponse execute(HttpRequest request, RequestOptions options, long start, long budgetNanos) {
        boolean repeatable = request.body() == null || request.body().repeatable();
        for (int attempt = 0; ; attempt++) {
            checkInterrupted();
            Duration remaining = remaining(start, budgetNanos);
            HttpResponse response;
            try {
                response = delegate.execute(request, boundedOptions(options, remaining));
            } catch (OpenAIIoException failure) {
                Duration delay = min(DEFAULT_RETRY_DELAY, maxRetryDelay);
                boolean interrupted = preserveInterruption(failure);
                if (attempt >= maxRetries || !repeatable || interrupted
                        || !canWait(delay, start, budgetNanos)) {
                    throw failure;
                }
                pause(delay);
                continue;
            }
            if (attempt >= maxRetries || !repeatable || !retryableStatus(response)) return response;
            if (response.statusCode() == 429) {
                response = bufferError(response);
                if (!((BufferedError) response).retryable()) return response;
            }
            Duration delay = retryDelay(response.headers());
            if (!canWait(delay, start, budgetNanos)) return response;
            response.close();
            pause(delay);
        }
    }

    private Duration budget(RequestOptions options) {
        Duration budget = min(callBudget, FeedbackRequestDeadline.remaining(callBudget));
        if (options.getTimeout() != null && !options.getTimeout().request().isZero()) {
            budget = min(budget, options.getTimeout().request());
        }
        return budget;
    }

    private Duration remaining(long start, long budgetNanos) {
        long left = budgetNanos - (nanoTime.getAsLong() - start);
        if (left <= 0) throw new OpenAIIoException("OpenAI request time budget exhausted", new SocketTimeoutException());
        return Duration.ofNanos(left);
    }

    private boolean canWait(Duration delay, long start, long budgetNanos) {
        return delay.compareTo(maxRetryDelay) <= 0
                && delay.toNanos() < budgetNanos - (nanoTime.getAsLong() - start);
    }

    private static RequestOptions boundedOptions(RequestOptions options, Duration remaining) {
        var timeout = options.getTimeout();
        var bounded = Timeout.builder().request(remaining);
        if (timeout != null) bounded.connect(cap(timeout.connect(), remaining))
                .read(cap(timeout.read(), remaining)).write(cap(timeout.write(), remaining));
        var builder = RequestOptions.builder().timeout(bounded.build());
        if (options.getResponseValidation() != null) builder.responseValidation(options.getResponseValidation());
        return builder.build();
    }

    private static boolean retryableStatus(HttpResponse response) {
        if ("false".equalsIgnoreCase(header(response.headers(), "X-Should-Retry"))) return false;
        int status = response.statusCode();
        return status == 408 || status == 409 || status == 429 || (status >= 500 && status < 600);
    }

    private static BufferedError bufferError(HttpResponse response) {
        try {
            InputStream remainder = response.body();
            byte[] prefix = remainder.readNBytes(MAX_ERROR_BODY_BYTES + 1);
            boolean retryable = false;
            if (prefix.length <= MAX_ERROR_BODY_BYTES) {
                try {
                    var tree = MAPPER.readTree(prefix);
                    if (tree != null) {
                        var error = tree.path("error");
                        String code = error.path("code").asString("");
                        String type = error.path("type").asString("");
                        retryable = ("rate_limit_exceeded".equals(code) && RATE_LIMIT_TYPES.contains(type))
                                || (code.isBlank() && "rate_limit_exceeded".equals(type));
                    }
                } catch (JacksonException malformed) {
                    // 분류할 수 없는 429는 재송신하지 않고 원래 본문을 SDK 오류 처리에 전달한다.
                }
            }
            return new BufferedError(response, prefix, remainder, retryable);
        } catch (IOException failure) {
            response.close();
            throw new OpenAIIoException("Unable to read rate-limit response", failure);
        }
    }

    private Duration retryDelay(Headers headers) {
        Duration delay = numberDelay(header(headers, "Retry-After-Ms"), 1_000_000);
        if (delay != null) return delay;
        String retryAfter = header(headers, "Retry-After");
        delay = numberDelay(retryAfter, 1_000_000_000);
        if (delay != null) return delay;
        try {
            delay = Duration.between(clock.instant(), ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            if (!delay.isNegative()) return delay;
        } catch (DateTimeParseException ignored) { }
        return min(DEFAULT_RETRY_DELAY, maxRetryDelay);
    }

    private static Duration numberDelay(String value, long nanosPerUnit) {
        try {
            var number = new BigDecimal(value);
            if (number.signum() < 0) return null;
            var nanos = number.multiply(BigDecimal.valueOf(nanosPerUnit));
            return Duration.ofNanos(nanos.min(BigDecimal.valueOf(Long.MAX_VALUE)).longValue());
        } catch (NumberFormatException ignored) { return null; }
        catch (ArithmeticException oversized) { return Duration.ofNanos(Long.MAX_VALUE); }
    }

    private static String header(Headers headers, String name) {
        var values = headers.values(name);
        return values.isEmpty() ? "" : values.getFirst().strip();
    }

    private void pause(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new OpenAIIoException("OpenAI retry interrupted", new InterruptedIOException());
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new OpenAIIoException("OpenAI request interrupted", new InterruptedIOException());
        }
    }

    private static boolean preserveInterruption(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            // OkHttp의 요청 시간 초과도 InterruptedIOException이다. 그 타입만으로 취소를 단정하지 않는다.
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                return true;
            }
            if (cause == cause.getCause()) break;
        }
        return Thread.currentThread().isInterrupted();
    }

    private static Duration min(Duration left, Duration right) { return left.compareTo(right) <= 0 ? left : right; }
    private static Duration cap(Duration original, Duration remaining) { return original.isZero() ? remaining : min(original, remaining); }

    @Override
    public CompletableFuture<HttpResponse> executeAsync(HttpRequest request, RequestOptions options) {
        long start = nanoTime.getAsLong();
        long budgetNanos = budget(options).toNanos(); // 호출 스레드의 전체 요청 예산을 작업 스레드에 전달한다.
        var result = new CompletableFuture<HttpResponse>();
        var task = executor.submit(() -> {
            try {
                var response = execute(request, options, start, budgetNanos);
                if (!result.complete(response)) response.close();
            }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        result.whenComplete((response, failure) -> { if (result.isCancelled()) task.cancel(true); });
        return result;
    }

    @Override public void close() { executor.shutdownNow(); delegate.close(); }

    @FunctionalInterface interface RetrySleeper { void sleep(Duration delay) throws InterruptedException; }

    private record BufferedError(HttpResponse original, byte[] prefix, InputStream remainder, boolean retryable) implements HttpResponse {
        @Override public int statusCode() { return original.statusCode(); }
        @Override public Headers headers() { return original.headers(); }
        @Override public InputStream body() { return new SequenceInputStream(new ByteArrayInputStream(prefix), remainder); }
        @Override public void close() { original.close(); }
    }
}
