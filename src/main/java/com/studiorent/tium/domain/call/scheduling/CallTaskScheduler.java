package com.studiorent.tium.domain.call.scheduling;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

@Component
public class CallTaskScheduler {

    private final ThreadPoolTaskScheduler scheduler = createScheduler();

    /** Schedules an in-memory CALL task; pending timers are not restored after server restart. */
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return scheduler.schedule(task, startTime);
    }

    /** Stops the shared CALL scheduler and discards pending timers on shutdown. */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /** Creates the single scheduler used by ringing and socket disconnect timeouts. */
    private static ThreadPoolTaskScheduler createScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("call-timeout-");
        scheduler.initialize();
        return scheduler;
    }
}
