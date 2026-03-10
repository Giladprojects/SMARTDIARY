package com.example.demo.scheduler;

public enum SchedulingDecisionType {
    NO_CONFLICT,
    SUGGEST_ALTERNATIVES,
    SHIFT_CONFLICTING_EVENTS,
    HARD_CONFLICT
}
