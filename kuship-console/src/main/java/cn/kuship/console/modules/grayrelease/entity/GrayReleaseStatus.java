package cn.kuship.console.modules.grayrelease.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GrayReleaseStatus {
    ACTIVE("active"),
    COMPLETED("completed"),
    CANCELLED("cancelled");

    private final String value;

    GrayReleaseStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static GrayReleaseStatus fromValue(String s) {
        if (s == null) return null;
        for (GrayReleaseStatus v : values()) {
            if (v.value.equalsIgnoreCase(s)) return v;
        }
        throw new IllegalArgumentException("unknown gray release status: " + s);
    }
}
