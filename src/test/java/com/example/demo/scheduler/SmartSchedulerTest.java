package com.example.demo.scheduler;

import com.example.demo.model.Event;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartSchedulerTest {

    private final SmartScheduler scheduler = new SmartScheduler();

    @Test
    void noConflictReturnsNoConflictDecision() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        Event existing = event(1, "Existing", date.atTime(9, 0), date.atTime(10, 0), 3);
        Event incoming = event(2, "Incoming", date.atTime(10, 0), date.atTime(11, 0), 3);

        SchedulingDecision decision = scheduler.decide(incoming, List.of(existing));

        assertEquals(SchedulingDecisionType.NO_CONFLICT, decision.getType());
    }

    @Test
    void higherPriorityEventSuggestsShift() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        Event existing = event(1, "Low", date.atTime(9, 0), date.atTime(10, 0), 1);
        Event incoming = event(2, "High", date.atTime(9, 30), date.atTime(10, 30), 5);

        SchedulingDecision decision = scheduler.decide(incoming, List.of(existing));

        assertEquals(SchedulingDecisionType.SHIFT_CONFLICTING_EVENTS, decision.getType());
        assertFalse(decision.getShifts().isEmpty());
        assertEquals(LocalDateTime.of(2026, 3, 10, 10, 30), decision.getShifts().get(0).getNewStart());
    }

    @Test
    void equalPrioritySuggestsAlternativeSlots() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        Event existing = event(1, "Block", date.atTime(9, 0), date.atTime(11, 0), 3);
        Event incoming = event(2, "New", date.atTime(9, 30), date.atTime(10, 30), 3);

        SchedulingDecision decision = scheduler.decide(incoming, List.of(existing));

        assertEquals(SchedulingDecisionType.SUGGEST_ALTERNATIVES, decision.getType());
        assertFalse(decision.getAlternatives().isEmpty());
        TimeSlot candidate = decision.getAlternatives().get(0);
        assertTrue(candidate.getEnd().isEqual(candidate.getStart().plusHours(1)));
        boolean overlaps = candidate.getStart().isBefore(existing.getEndTime())
                && candidate.getEnd().isAfter(existing.getStartTime());
        assertTrue(!overlaps);
    }

    @Test
    void fixedParticipantConflictFallsBackToAlternatives() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        Event existing = new Event(1, 99, "Participant Busy", date.atTime(9, 0), date.atTime(10, 0), 1, "", "");
        Event incoming = new Event(2, 1, "Organizer Event", date.atTime(9, 0), date.atTime(10, 0), 5, "", "");

        SchedulingDecision decision = scheduler.decide(incoming, List.of(existing), event -> event.getUserId() == 1);

        assertEquals(SchedulingDecisionType.SUGGEST_ALTERNATIVES, decision.getType());
        assertFalse(decision.getAlternatives().isEmpty());
    }

    private Event event(int id, String title, LocalDateTime start, LocalDateTime end, int priority) {
        return new Event(id, title, start, end, priority, "", "");
    }
}
