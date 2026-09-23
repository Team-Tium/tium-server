package com.studiorent.tium.domain.feedback.entity.enums;


public enum RelationShip {
    FRIEND("friend"),
    STRANGER("stranger");

    private final String ragValue;

    RelationShip(String ragValue) {
        this.ragValue = ragValue;
    }

    public String getRagValue() {
        return ragValue;
    }
}