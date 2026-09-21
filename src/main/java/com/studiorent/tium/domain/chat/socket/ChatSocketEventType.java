package com.studiorent.tium.domain.chat.socket;

public final class ChatSocketEventType {

    public static final String MESSAGE_CREATED = "MESSAGE_CREATED";
    public static final String ROOM_UPDATED = "ROOM_UPDATED";
    public static final String MESSAGE_READ = "MESSAGE_READ";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";

    private ChatSocketEventType() {
    }
}
