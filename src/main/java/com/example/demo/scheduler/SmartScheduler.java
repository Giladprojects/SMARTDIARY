package com.example.demo.scheduler;

import com.example.demo.model.Event;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class SmartScheduler {

    private static final int STEP_MINUTES = 30;
    private static final int MAX_ALTERNATIVES = 3;
    private static final Comparator<Event> BY_START = Comparator
            .comparing(Event::getStartTime)
            .thenComparing(Event::getEndTime)
            .thenComparingInt(Event::getId)
            .thenComparing(Event::getTitle)
            .thenComparing(Event::getLocation)
            .thenComparingInt(Event::getPriority);
    private static final Comparator<Event> BY_END = Comparator
            .comparing(Event::getEndTime)
            .thenComparing(Event::getStartTime)
            .thenComparingInt(Event::getId)
            .thenComparing(Event::getTitle)
            .thenComparing(Event::getLocation)
            .thenComparingInt(Event::getPriority);

    public SchedulingDecision decide(Event newEvent, List<Event> allEvents) {
        EventIndex index = buildIndex(allEvents);
        List<Event> conflicts = findConflicts(newEvent, index);
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

        List<TimeSlot> alternatives = findAlternativeSlots(newEvent, index);
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
        return findConflicts(target, buildIndex(allEvents));
    }

    private List<Event> findConflicts(Event target, EventIndex index) {
        return findConflicts(target.getStartTime(), target.getEndTime(), index, target.getId());
    }

    private List<Event> findConflicts(
            LocalDateTime start,
            LocalDateTime end,
            EventIndex index,
            int excludeEventId
    ) {
        List<Event> conflicts = new ArrayList<>();
        DayIndex day = index.days.get(start.toLocalDate());
        if (day == null) {
            return conflicts;
        }

        Event byStartProbe = probeByStart(end);
        Event byEndProbe = probeByEnd(start);

        NavigableSet<Event> startCandidates = day.byStart.headSet(byStartProbe, false);
        NavigableSet<Event> endCandidates = day.byEnd.tailSet(byEndProbe, false);

        Collection<Event> primary;
        if (startCandidates.size() <= endCandidates.size()) {
            primary = startCandidates;
        } else {
            primary = endCandidates;
        }

        for (Event event : primary) {
            if (event.getId() == excludeEventId && excludeEventId != 0) {
                continue;
            }
            if (overlaps(start, end, event.getStartTime(), event.getEndTime())) {
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
        EventIndex fixedIndex = buildIndex(fixedEvents);

        List<Event> toShift = new ArrayList<>(conflicts);
        toShift.sort(Comparator.comparingInt(Event::getPriority));

        for (Event conflict : toShift) {
            Duration duration = Duration.between(conflict.getStartTime(), conflict.getEndTime());
            TimeSlot slot = findFirstFreeSlot(
                    conflict.getStartTime().toLocalDate(),
                    newEvent.getEndTime(),
                    duration,
                    fixedIndex
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
            fixedIndex.add(shiftedCopy);
        }

        return shifts;
    }

    private List<TimeSlot> findAlternativeSlots(Event newEvent, EventIndex index) {
        List<TimeSlot> alternatives = new ArrayList<>();
        Duration duration = Duration.between(newEvent.getStartTime(), newEvent.getEndTime());
        LocalDate date = newEvent.getStartTime().toLocalDate();
        LocalDateTime requestedStart = newEvent.getStartTime();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        for (LocalDateTime candidateStart = date.atStartOfDay();
             candidateStart.plus(duration).isBefore(dayEnd) || candidateStart.plus(duration).isEqual(dayEnd);
             candidateStart = candidateStart.plusMinutes(STEP_MINUTES)) {

            LocalDateTime candidateEnd = candidateStart.plus(duration);
            if (isFree(candidateStart, candidateEnd, index)) {
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
            EventIndex index
    ) {
        LocalDateTime cursor = roundUpToStep(notBefore);
        if (!cursor.toLocalDate().equals(date)) {
            cursor = date.atStartOfDay();
        }

        while (cursor.toLocalDate().equals(date) && !cursor.plus(duration).toLocalTime().isAfter(LocalTime.MAX)) {
            LocalDateTime end = cursor.plus(duration);
            if (isFree(cursor, end, index)) {
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

    private boolean isFree(LocalDateTime start, LocalDateTime end, EventIndex index) {
        return findConflicts(start, end, index, 0).isEmpty();
    }

    private boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private EventIndex buildIndex(List<Event> events) {
        EventIndex index = new EventIndex();
        for (Event event : events) {
            index.add(event);
        }
        return index;
    }

    private Event probeByStart(LocalDateTime start) {
        return new Event(
                Integer.MIN_VALUE,
                "",
                start,
                start,
                1,
                "",
                ""
        );
    }

    private Event probeByEnd(LocalDateTime end) {
        return new Event(
                Integer.MIN_VALUE,
                "",
                end,
                end,
                1,
                "",
                ""
        );
    }

    private static final class DayIndex {
        private final NavigableSet<Event> byStart = new TreeSet<>(BY_START);
        private final NavigableSet<Event> byEnd = new TreeSet<>(BY_END);

        private void add(Event event) {
            byStart.add(event);
            byEnd.add(event);
        }
    }

    private static final class EventIndex {
        private final TreeMap<LocalDate, DayIndex> days = new TreeMap<>();

        private void add(Event event) {
            LocalDate dayKey = event.getStartTime().toLocalDate();
            DayIndex day = days.computeIfAbsent(dayKey, ignored -> new DayIndex());
            day.add(event);
        }
    }
}
