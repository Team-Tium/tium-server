package com.studiorent.tium.global.socket;

public interface SubscriptionAuthorizer {

    boolean supports(String destination);

    boolean canSubscribe(Long memberId, String destination);
}
