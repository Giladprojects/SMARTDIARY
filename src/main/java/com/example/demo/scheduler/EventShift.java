package com.example.demo.scheduler;

import com.example.demo.model.Event;
import java.time.LocalDateTime;

public class EventShift {
    private final Event event;
    private final LocalDateTime newStart;
    private final LocalDateTime newEnd;

    public EventShift(Event event, LocalDateTime newStart, LocalDateTime newEnd) {
        this.event = event;
        this.newStart = newStart;
        this.newEnd = newEnd;
    }

    public Event getEvent() {
        return event;
    }

    public LocalDateTime getNewStart() {
        return newStart;
    }

    public LocalDateTime getNewEnd() {
        return newEnd;
    }
}
