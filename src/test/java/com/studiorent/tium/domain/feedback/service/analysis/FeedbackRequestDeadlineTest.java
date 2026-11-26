package com.studiorent.tium.domain.feedback.service.analysis;

import com.openai.errors.OpenAIIoException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedbackRequestDeadlineTest {
    @Test
    void 여러_호출이_예산을_공유하고_개별_호출_상한도_유지한다() {
        var clock = new AtomicLong();
        FeedbackRequestDeadline.within(Duration.ofSeconds(120), clock::get, () -> {
            assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
            clock.set(Duration.ofSeconds(90).toNanos());
            assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(30));
            return null;
        });
        assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 중첩_호출은_기존_마감보다_늦어질_수_없고_종료_후_기존_문맥을_복원한다() {
        var clock = new AtomicLong();
        FeedbackRequestDeadline.within(Duration.ofSeconds(100), clock::get, () -> {
            clock.set(Duration.ofSeconds(40).toNanos());
            FeedbackRequestDeadline.within(Duration.ofSeconds(500), clock::get, () -> {
                assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(500))).isEqualTo(Duration.ofSeconds(60));
                clock.addAndGet(Duration.ofSeconds(10).toNanos());
                return null;
            });
            assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(100))).isEqualTo(Duration.ofSeconds(50));
            return null;
        });
    }

    @Test
    void 예산_소진은_타임아웃으로_반환하고_다음_요청에_마감을_남기지_않는다() {
        var clock = new AtomicLong();
        assertThatThrownBy(() -> FeedbackRequestDeadline.within(Duration.ofSeconds(1), clock::get, () -> {
            clock.set(Duration.ofSeconds(1).toNanos());
            return FeedbackRequestDeadline.remaining(Duration.ofSeconds(60));
        })).isInstanceOf(OpenAIIoException.class).hasCauseInstanceOf(SocketTimeoutException.class);

        assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void 처리_중_다른_예외가_나도_요청_문맥을_정리한다() {
        var failure = new IllegalStateException("synthetic failure");
        assertThatThrownBy(() -> FeedbackRequestDeadline.within(Duration.ofSeconds(1), () -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(FeedbackRequestDeadline.remaining(Duration.ofSeconds(60))).isEqualTo(Duration.ofSeconds(60));
    }
}
