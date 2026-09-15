package com.studiorent.tium.global.socket;

public interface SocketEventPublisher {

    void toDestination(String destination, SocketEvent<?> event);

    void toMember(Long memberId, SocketEvent<?> event);
}
