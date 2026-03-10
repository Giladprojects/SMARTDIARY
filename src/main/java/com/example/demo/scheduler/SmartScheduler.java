package com.example.demo.scheduler;

import com.example.demo.model.Event;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SmartScheduler {

    private static final int STEP_MINUTES = 30;
    private static final int MAX_ALTERNATIVES = 3;

    public SchedulingDecision decide(Event newEvent, List<Event> allEvents) {
        List<Event> conflicts = findConflicts(newEvent, allEvents);
        if (conflicts.isEmpty()) {
            return new SchedulingDecision(
                    SchedulingDecisionType.NO_CONFLICT,
                    "No conflicts found.",
                    List.of(),
                    List.of()
            );
        }

        int maxConflictPriority = conflicts.stream()
                .map(Event::getPriority)
                .max(Integer::compareTo)
                .orElse(1);

        if (newEvent.getPriority() > maxConflictPriority) {
            List<EventShift> shifts = proposeShifts(newEvent, allEvents, conflicts);
            if (shifts.size() == conflicts.size()) {
                return new SchedulingDecision(
                        SchedulingDecisionType.SHIFT_CONFLICTING_EVENTS,
                        "New event has higher priority. Recommended to shift conflicting events.",
                        List.of(),
                        shifts
                );
            }
        }

        List<TimeSlot> alternatives = findAlternativeSlots(newEvent, allEvents);
        if (!alternatives.isEmpty()) {
            return new SchedulingDecision(
                    SchedulingDecisionType.SUGGEST_ALTERNATIVES,
                    "Conflicts found. Recommended to move the new event to an available slot.",
                    alternatives,
                    List.of()
            );
        }

        return new SchedulingDecision(
                SchedulingDecisionType.HARD_CONFLICT,
                "Conflicts found and no good automatic alternative was detected.",
                List.of(),
                List.of()
        );
    }

    public List<Event> findConflicts(Event target, List<Event> allEvents) {
        List<Event> conflicts = new ArrayList<>();
        for (Event event : allEvents) {
            if (event.getId() == target.getId()) {
                continue;
            }
            if (overlaps(target.getStartTime(), target.getEndTime(), event.getStartTime(), event.getEndTime())) {
                conflicts.add(event);
            }
        }
        return conflicts;
    }

    private List<EventShift> proposeShifts(Event newEvent, List<Event> allEvents, List<Event> conflicts) {
        List<EventShift> shifts = new ArrayList<>();
        List<Event> fixedEvents = new ArrayList<>(allEvents);
        fixedEvents.removeAll(conflicts);
        fixedEvents.add(newEvent);

        List<Event> toShift = new ArrayList<>(conflicts);
        toShift.sort(Comparator.comparingInt(Event::getPriority));

        for (Event conflict : toShift) {
            Duration duration = Duration.between(conflict.getStartTime(), conflict.getEndTime());
            TimeSlot slot = findFirstFreeSlot(
                    conflict.getStartTime().toLocalDate(),
                    newEvent.getEndTime(),
                    duration,
                    fixedEvents
            );
            if (slot == null) {
                return List.of();
            }

            shifts.add(new EventShift(conflict, slot.getStart(), slot.getEnd()));

            Event shiftedCopy = new Event(
                    conflict.getId(),
                    conflict.getTitle(),
                    slot.getStart(),
                    slot.getEnd(),
                    conflict.getPriority(),
                    conflict.getDescription(),
                    conflict.getLocation()
            );
            fixedEvents.add(shiftedCopy);
        }

        return shifts;
    }

    private List<TimeSlot> findAlternativeSlots(Event newEvent, List<Event> allEvents) {
        List<TimeSlot> alternatives = new ArrayList<>();
        Duration duration = Duration.between(newEvent.getStartTime(), newEvent.getEndTime());
        LocalDate date = newEvent.getStartTime().toLocalDate();
        LocalDateTime requestedStart = newEvent.getStartTime();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        for (LocalDateTime candidateStart = date.atStartOfDay();
             candidateStart.plus(duration).isBefore(dayEnd) || candidateStart.plus(duration).isEqual(dayEnd);
             candidateStart = candidateStart.plusMinutes(STEP_MINUTES)) {

            LocalDateTime candidateEnd = candidateStart.plus(duration);
            if (isFree(candidateStart, candidateEnd, allEvents)) {
                alternatives.add(new TimeSlot(candidateStart, candidateEnd));
            }
        }

        alternatives.sort(Comparator.comparingLong(slot ->
                Math.abs(Duration.between(requestedStart, slot.getStart()).toMinutes())));

        if (alternatives.size() > MAX_ALTERNATIVES) {
            return alternatives.subList(0, MAX_ALTERNATIVES);
        }
        return alternatives;
    }

    private TimeSlot findFirstFreeSlot(
            LocalDate date,
            LocalDateTime notBefore,
            Duration duration,
            List<Event> events
    ) {
        LocalDateTime cursor = roundUpToStep(notBefore);
        if (!cursor.toLocalDate().equals(date)) {
            cursor = date.atStartOfDay();
        }

        while (cursor.toLocalDate().equals(date) && !cursor.plus(duration).toLocalTime().isAfter(LocalTime.MAX)) {
            LocalDateTime end = cursor.plus(duration);
            if (isFree(cursor, end, events)) {
                return new TimeSlot(cursor, end);
            }
            cursor = cursor.plusMinutes(STEP_MINUTES);
        }
        return null;
    }

    private LocalDateTime roundUpToStep(LocalDateTime time) {
        int minute = time.getMinute();
        int mod = minute % STEP_MINUTES;
        if (mod == 0) {
            return time.withSecond(0).withNano(0);
        }
        return time.plusMinutes(STEP_MINUTES - mod).withSecond(0).withNano(0);
    }

    private boolean isFree(LocalDateTime start, LocalDateTime end, List<Event> events) {
        for (Event event : events) {
            if (overlaps(start, end, event.getStartTime(), event.getEndTime())) {
                return false;
            }
        }
        return true;
    }

    private boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
