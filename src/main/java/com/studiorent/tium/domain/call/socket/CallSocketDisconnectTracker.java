package com.studiorent.tium.domain.call.socket;

import com.studiorent.tium.domain.call.scheduling.CallTaskScheduler;
import com.studiorent.tium.domain.call.service.command.CallDisconnectCommandService;
import com.studiorent.tium.global.security.CustomUserDetails;
import com.studiorent.tium.global.socket.SocketDestinations;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
public class CallSocketDisconnectTracker {

    private static final Duration DISCONNECT_GRACE = Duration.ofSeconds(15);

    private final CallDisconnectCommandService callDisconnectCommandService;
    private final CallTaskScheduler scheduler;

    private final Map<String, Map<String, CallSubscription>> sessionSubscriptions = new ConcurrentHashMap<>();
    private final Map<CallMemberKey, Set<SubscriptionKey>> activeSubscriptions = new ConcurrentHashMap<>();
    private final Map<CallMemberKey, PendingDisconnect> pendingDisconnects = new ConcurrentHashMap<>();

    /** Tracks active call-topic subscriptions and cancels pending disconnect termination on return. */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long callId = parseCallId(accessor.getDestination());
        Long memberId = extractMemberId(accessor.getUser());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        if (callId == null || memberId == null || !StringUtils.hasText(sessionId)
                || !StringUtils.hasText(subscriptionId)) {
            return;
        }

        CallSubscription subscription = new CallSubscription(sessionId, subscriptionId, callId, memberId);
        sessionSubscriptions.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(subscriptionId, subscription);
        activeSubscriptions.computeIfAbsent(subscription.callMemberKey(), ignored -> ConcurrentHashMap.newKeySet())
                .add(subscription.subscriptionKey());
        cancelPendingDisconnect(subscription.callMemberKey());
    }

    /** Removes explicit unsubscriptions from the active subscription index. */
    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(subscriptionId)) {
            return;
        }

        Map<String, CallSubscription> subscriptions = sessionSubscriptions.get(sessionId);
        if (subscriptions == null) {
            return;
        }

        CallSubscription subscription = subscriptions.remove(subscriptionId);
        if (subscription != null) {
            removeActiveSubscription(subscription);
        }
        if (subscriptions.isEmpty()) {
            sessionSubscriptions.remove(sessionId, subscriptions);
        }
    }

    /** Starts grace timers for call subscriptions lost with a disconnected STOMP session. */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        Map<String, CallSubscription> removedSubscriptions = sessionSubscriptions.remove(sessionId);
        if (removedSubscriptions == null || removedSubscriptions.isEmpty()) {
            return;
        }

        LocalDateTime disconnectedAt = LocalDateTime.now();
        removedSubscriptions.values().forEach(subscription -> {
            removeActiveSubscription(subscription);
            CallMemberKey key = subscription.callMemberKey();
            if (!hasActiveSubscription(key)) {
                scheduleDisconnect(key, disconnectedAt);
            }
        });
    }

    /** Cancels this tracker's pending disconnects before the shared CALL scheduler shuts down. */
    @PreDestroy
    public void shutdown() {
        pendingDisconnects.values().forEach(pending -> pending.future().cancel(false));
    }

    /** Parses only exact /sub/call/{callId} destinations. */
    private Long parseCallId(String destination) {
        String prefix = SocketDestinations.CALL_SUB_PREFIX + "/";
        if (!StringUtils.hasText(destination) || !destination.startsWith(prefix)) {
            return null;
        }

        String callIdText = destination.substring(prefix.length());
        if (callIdText.isBlank() || callIdText.contains("/")) {
            return null;
        }

        try {
            return Long.valueOf(callIdText);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Extracts the authenticated member ID attached by shared socket authentication. */
    private Long extractMemberId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }
        return null;
    }

    /** Removes one tracked subscription from the per-call active index. */
    private void removeActiveSubscription(CallSubscription subscription) {
        CallMemberKey key = subscription.callMemberKey();
        Set<SubscriptionKey> subscriptions = activeSubscriptions.get(key);
        if (subscriptions == null) {
            return;
        }

        subscriptions.remove(subscription.subscriptionKey());
        if (subscriptions.isEmpty()) {
            activeSubscriptions.remove(key, subscriptions);
        }
    }

    /** Returns whether the member still has any active subscription to the call. */
    private boolean hasActiveSubscription(CallMemberKey key) {
        Set<SubscriptionKey> subscriptions = activeSubscriptions.get(key);
        return subscriptions != null && !subscriptions.isEmpty();
    }

    /** Schedules one disconnect termination unless an earlier timer is already pending. */
    private void scheduleDisconnect(CallMemberKey key, LocalDateTime disconnectedAt) {
        pendingDisconnects.computeIfAbsent(key, ignored -> {
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> completeDisconnectAfterGrace(key, disconnectedAt),
                    Instant.now().plus(DISCONNECT_GRACE)
            );
            return new PendingDisconnect(future);
        });
    }

    /** Cancels a pending disconnect when the same member returns to the call topic. */
    private void cancelPendingDisconnect(CallMemberKey key) {
        PendingDisconnect pending = pendingDisconnects.remove(key);
        if (pending != null) {
            pending.future().cancel(false);
        }
    }

    /** Completes a pending disconnect only if the member did not resubscribe during grace. */
    private void completeDisconnectAfterGrace(CallMemberKey key, LocalDateTime disconnectedAt) {
        PendingDisconnect pending = pendingDisconnects.remove(key);
        if (pending == null || hasActiveSubscription(key)) {
            return;
        }

        callDisconnectCommandService.disconnectIfStillActive(key.callId(), key.memberId(), disconnectedAt);
    }

    private record CallSubscription(String sessionId, String subscriptionId, Long callId, Long memberId) {
        private CallMemberKey callMemberKey() {
            return new CallMemberKey(callId, memberId);
        }

        private SubscriptionKey subscriptionKey() {
            return new SubscriptionKey(sessionId, subscriptionId);
        }
    }

    private record CallMemberKey(Long callId, Long memberId) {
    }

    private record SubscriptionKey(String sessionId, String subscriptionId) {
    }

    private record PendingDisconnect(ScheduledFuture<?> future) {
    }
}
