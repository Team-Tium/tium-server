package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIIoException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 분석, 평가 및 재시도가 하나의 피드백 시간 예산을 공유한다. 요청 종료 시 문맥을 반드시 정리한다. */
public final class FeedbackRequestDeadline {
    private static final ThreadLocal<Deadline> CURRENT = new ThreadLocal<>();

    private FeedbackRequestDeadline() { }

    public static <T> T within(Duration budget, Supplier<T> operation) {
        return within(budget, System::nanoTime, operation);
    }

    static <T> T within(Duration budget, LongSupplier clock, Supplier<T> operation) {
        requirePositive(budget);
        var previous = CURRENT.get();
        Duration effectiveBudget = remaining(budget);
        CURRENT.set(new Deadline(clock.getAsLong(), effectiveBudget.toNanos(), clock));
        try {
            return operation.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static Duration remaining(Duration callBudget) {
        requirePositive(callBudget);
        var deadline = CURRENT.get();
        if (deadline == null) return callBudget;
        long remaining = deadline.budgetNanos() - (deadline.clock().getAsLong() - deadline.startedNanos());
        if (remaining <= 0) {
            throw new OpenAIIoException("Feedback request deadline exceeded", new SocketTimeoutException());
        }
        return Duration.ofNanos(Math.min(callBudget.toNanos(), remaining));
    }

    private static void requirePositive(Duration budget) {
        if (budget == null || budget.isZero() || budget.isNegative()) {
            throw new IllegalArgumentException("Feedback timeout must be positive");
        }
    }

    private record Deadline(long startedNanos, long budgetNanos, LongSupplier clock) { }
}
