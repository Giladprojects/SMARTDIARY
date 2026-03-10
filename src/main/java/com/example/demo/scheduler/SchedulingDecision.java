package com.example.demo.scheduler;

import java.util.Collections;
import java.util.List;

public class SchedulingDecision {
    private final SchedulingDecisionType type;
    private final String explanation;
    private final List<TimeSlot> alternatives;
    private final List<EventShift> shifts;

    public SchedulingDecision(
            SchedulingDecisionType type,
            String explanation,
            List<TimeSlot> alternatives,
            List<EventShift> shifts
    ) {
        this.type = type;
        this.explanation = explanation;
        this.alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        this.shifts = shifts == null ? List.of() : List.copyOf(shifts);
    }

    public SchedulingDecisionType getType() {
        return type;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<TimeSlot> getAlternatives() {
        return Collections.unmodifiableList(alternatives);
    }

    public List<EventShift> getShifts() {
        return Collections.unmodifiableList(shifts);
    }
}
