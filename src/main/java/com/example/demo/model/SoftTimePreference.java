package com.example.demo.model;

import java.time.LocalTime;

public enum SoftTimePreference {
    ANY_TIME("ANY_TIME"),
    MORNING("MORNING"),
    AFTERNOON("AFTERNOON"),
    EVENING("EVENING");

    private final String dbValue;

    SoftTimePreference(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public boolean matches(LocalTime startTime) {
        return switch (this) {
            case ANY_TIME -> true;
            case MORNING -> !startTime.isBefore(LocalTime.of(6, 0)) && startTime.isBefore(LocalTime.NOON);
            case AFTERNOON -> !startTime.isBefore(LocalTime.NOON) && startTime.isBefore(LocalTime.of(17, 0));
            case EVENING -> !startTime.isBefore(LocalTime.of(17, 0)) && startTime.isBefore(LocalTime.of(22, 0));
        };
    }

    public static SoftTimePreference fromDbValue(String value) {
        if (value == null || value.isBlank()) {
            return ANY_TIME;
        }

        for (SoftTimePreference preference : values()) {
            if (preference.dbValue.equalsIgnoreCase(value.trim())) {
                return preference;
            }
        }
        return ANY_TIME;
    }
}
