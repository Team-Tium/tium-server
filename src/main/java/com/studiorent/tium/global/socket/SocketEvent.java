package com.studiorent.tium.global.socket;

public record SocketEvent<T>(
        String type,
        T data
) {
}
