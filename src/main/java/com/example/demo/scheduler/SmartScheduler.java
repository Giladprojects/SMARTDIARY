package com.example.demo.scheduler;

import com.example.demo.model.Event;
import com.example.demo.model.SoftTimePreference;

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
import java.util.function.Predicate;

public class SmartScheduler {

    private static final int STEP_MINUTES = 30;
    private static final int MAX_ALTERNATIVES = 3;
    private static final int NEARBY_DAYS_AHEAD = 2;
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
        return decide(newEvent, allEvents, ignored -> true);
    }

    public SchedulingDecision decide(Event newEvent, List<Event> allEvents, Predicate<Event> canShift) {
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
        boolean hasImmovableConflicts = conflicts.stream().anyMatch(event -> !canMove(event, canShift));

        if (newEvent.getPriority() > maxConflictPriority && !hasImmovableConflicts) {
            List<EventShift> shifts = proposeShifts(newEvent, allEvents, conflicts, canShift);
            if (shifts.size() == conflicts.size()) {
                return new SchedulingDecision(
                        SchedulingDecisionType.SHIFT_CONFLICTING_EVENTS,
                        "New event has higher priority. Recommended to shift conflicting events.",
                        List.of(),
                        shifts
                );
            }
        }

        if (!newEvent.isImmovable()) {
            List<TimeSlot> alternatives = findAlternativeSlots(newEvent, index);
            if (!alternatives.isEmpty()) {
                String explanation = hasImmovableConflicts
                        ? "Conflicts found with fixed events that cannot be moved. Recommended to move the new event."
                        : "Conflicts found. Recommended to move the new event to an available slot.";
                return new SchedulingDecision(
                        SchedulingDecisionType.SUGGEST_ALTERNATIVES,
                        explanation,
                        alternatives,
                        List.of()
                );
            }
        }

        if (hasImmovableConflicts) {
            return new SchedulingDecision(
                    SchedulingDecisionType.HARD_CONFLICT,
                    newEvent.isImmovable()
                            ? "Conflicts found with fixed events. This event is also fixed and cannot be moved automatically."
                            : "Conflicts found with fixed events and no good automatic alternative was detected.",
                    List.of(),
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
            if ((event.getId() != excludeEventId || excludeEventId == 0)
                    && overlaps(start, end, event.getStartTime(), event.getEndTime())) {
                conflicts.add(event);
            }
        }
        return conflicts;
    }

    private List<EventShift> proposeShifts(
            Event newEvent,
            List<Event> allEvents,
            List<Event> conflicts,
            Predicate<Event> canShift
    ) {
        for (Event conflict : conflicts) {
            if (!canMove(conflict, canShift)) {
                return List.of();
            }
        }

        List<EventShift> shifts = new ArrayList<>();
        List<Event> fixedEvents = new ArrayList<>(allEvents);
        fixedEvents.removeAll(conflicts);
        fixedEvents.add(newEvent);
        EventIndex fixedIndex = buildIndex(fixedEvents);

        List<Event> toShift = new ArrayList<>(conflicts);
        toShift.sort(Comparator.comparingInt(Event::getPriority));

        for (Event conflict : toShift) {
            Duration duration = Duration.between(conflict.getStartTime(), conflict.getEndTime());
            TimeSlot slot = findBestFreeSlot(
                    conflict.getStartTime().toLocalDate(),
                    newEvent.getEndTime(),
                    duration,
                    fixedIndex,
                    conflict.getSoftTimePreference()
            );
            if (slot == null) {
                return List.of();
            }

            shifts.add(new EventShift(conflict, slot.getStart(), slot.getEnd()));

            Event shiftedCopy = new Event(
                    conflict.getId(),
                    conflict.getUserId(),
                    conflict.getTitle(),
                    slot.getStart(),
                    slot.getEnd(),
                    conflict.getPriority(),
                    conflict.getDescription(),
                    conflict.getLocation(),
                    conflict.getSoftTimePreference()
            );
            fixedIndex.add(shiftedCopy);
        }

        return shifts;
    }

    private boolean canMove(Event event, Predicate<Event> canShift) {
        return !event.isImmovable() && canShift.test(event);
    }

    private List<TimeSlot> findAlternativeSlots(Event newEvent, EventIndex index) {
        List<TimeSlot> alternatives = new ArrayList<>();
        Duration duration = Duration.between(newEvent.getStartTime(), newEvent.getEndTime());
        LocalDate requestedDate = newEvent.getStartTime().toLocalDate();
        LocalDateTime requestedStart = newEvent.getStartTime();

        for (int dayOffset = 0; dayOffset <= NEARBY_DAYS_AHEAD; dayOffset++) {
            collectFreeSlotsForDate(requestedDate.plusDays(dayOffset), duration, index, alternatives);
        }

        alternatives.sort(Comparator.<TimeSlot>comparingLong(slot ->
                preferencePenalty(newEvent.getSoftTimePreference(), slot.getStart()))
                .thenComparingLong(slot ->
                        Math.abs(Duration.between(requestedDate.atStartOfDay(), slot.getStart().toLocalDate().atStartOfDay()).toDays()))
                .thenComparingLong(slot -> timeOfDayDistanceMinutes(requestedStart, slot.getStart()))
                .thenComparing(TimeSlot::getStart));

        if (alternatives.size() > MAX_ALTERNATIVES) {
            return alternatives.subList(0, MAX_ALTERNATIVES);
        }
        return alternatives;
    }

    private void collectFreeSlotsForDate(
            LocalDate date,
            Duration duration,
            EventIndex index,
            List<TimeSlot> alternatives
    ) {
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        for (LocalDateTime candidateStart = date.atStartOfDay();
             !candidateStart.plus(duration).isAfter(dayEnd);
             candidateStart = candidateStart.plusMinutes(STEP_MINUTES)) {

            LocalDateTime candidateEnd = candidateStart.plus(duration);
            if (isFree(candidateStart, candidateEnd, index)) {
                alternatives.add(new TimeSlot(candidateStart, candidateEnd));
            }
        }
    }

    private long timeOfDayDistanceMinutes(LocalDateTime requestedStart, LocalDateTime candidateStart) {
        return Math.abs(Duration.between(requestedStart.toLocalTime(), candidateStart.toLocalTime()).toMinutes());
    }

    private TimeSlot findBestFreeSlot(
            LocalDate date,
            LocalDateTime notBefore,
            Duration duration,
            EventIndex index,
            SoftTimePreference preference
    ) {
        LocalDateTime cursor = roundUpToStep(notBefore);
        if (!cursor.toLocalDate().equals(date)) {
            cursor = date.atStartOfDay();
        }

        TimeSlot bestPreferred = null;
        TimeSlot bestFallback = null;
        while (cursor.toLocalDate().equals(date) && !cursor.plus(duration).toLocalTime().isAfter(LocalTime.MAX)) {
            LocalDateTime end = cursor.plus(duration);
            if (isFree(cursor, end, index)) {
                TimeSlot candidate = new TimeSlot(cursor, end);
                if (matchesPreference(preference, cursor)) {
                    bestPreferred = candidate;
                    break;
                }
                if (bestFallback == null) {
                    bestFallback = candidate;
                }
            }
            cursor = cursor.plusMinutes(STEP_MINUTES);
        }
        return bestPreferred != null ? bestPreferred : bestFallback;
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

    private boolean matchesPreference(SoftTimePreference preference, LocalDateTime start) {
        SoftTimePreference normalized = preference == null ? SoftTimePreference.ANY_TIME : preference;
        return normalized.matches(start.toLocalTime());
    }

    private long preferencePenalty(SoftTimePreference preference, LocalDateTime start) {
        return matchesPreference(preference, start) ? 0 : 1;
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
