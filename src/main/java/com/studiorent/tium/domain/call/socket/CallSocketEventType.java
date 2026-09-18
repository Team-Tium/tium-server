package com.studiorent.tium.domain.call.socket;

public final class CallSocketEventType {

    public static final String CALL_INCOMING = "CALL_INCOMING";
    public static final String CALL_ACCEPTED = "CALL_ACCEPTED";
    public static final String CALL_REJECTED = "CALL_REJECTED";
    public static final String CALL_SDP_OFFER = "CALL_SDP_OFFER";
    public static final String CALL_SDP_ANSWER = "CALL_SDP_ANSWER";
    public static final String CALL_ICE_CANDIDATE = "CALL_ICE_CANDIDATE";
    public static final String CALL_ENDED = "CALL_ENDED";

    private CallSocketEventType() {
    }
}
