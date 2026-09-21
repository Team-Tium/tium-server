package com.studiorent.tium.global.socket;

public final class SocketDestinations {

    public static final String WS_ENDPOINT = "/ws";

    public static final String APPLICATION_PREFIX = "/pub";
    public static final String BROKER_PREFIX = "/sub";

    public static final String CHAT_SUB_PREFIX = BROKER_PREFIX + "/chat";
    public static final String CHATROOM_SUB_PREFIX = CHAT_SUB_PREFIX + "/rooms";
    public static final String CALL_SUB_PREFIX = BROKER_PREFIX + "/call";
    public static final String MEMBER_SUB_PREFIX = BROKER_PREFIX + "/users";

    public static final String CALL_PUB_PREFIX = APPLICATION_PREFIX + "/call";

    private SocketDestinations() {
    }
}
