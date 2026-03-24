package com.example.demo.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecurringEventSeries {

    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";

    private int recurrenceId;
    private int userId;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int priority;
    private String description;
    private String location;
    private SoftTimePreference softTimePreference;
    private String frequency;
    private LocalDateTime untilDate;

    public RecurringEventSeries(
            int recurrenceId,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int priority,
            String description,
            String location,
            String frequency,
            LocalDateTime untilDate
    ) {
        this(
                recurrenceId,
                1,
                title,
                startTime,
                endTime,
                priority,
                description,
                location,
                SoftTimePreference.ANY_TIME,
                frequency,
                untilDate
        );
    }

    public RecurringEventSeries(
            int recurrenceId,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int priority,
            String description,
            String location,
            SoftTimePreference softTimePreference,
            String frequency,
            LocalDateTime untilDate
    ) {
        this(recurrenceId, 1, title, startTime, endTime, priority, description, location, softTimePreference, frequency, untilDate);
    }

    public RecurringEventSeries(
            int recurrenceId,
            int userId,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int priority,
            String description,
            String location,
            String frequency,
            LocalDateTime untilDate
    ) {
        this(
                recurrenceId,
                userId,
                title,
                startTime,
                endTime,
                priority,
                description,
                location,
                SoftTimePreference.ANY_TIME,
                frequency,
                untilDate
        );
    }

    public RecurringEventSeries(
            int recurrenceId,
            int userId,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int priority,
            String description,
            String location,
            SoftTimePreference softTimePreference,
            String frequency,
            LocalDateTime untilDate
    ) {
        this.recurrenceId = recurrenceId;
        this.userId = userId;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.priority = priority;
        this.description = description;
        this.location = location;
        this.softTimePreference = softTimePreference == null ? SoftTimePreference.ANY_TIME : softTimePreference;
        this.frequency = frequency;
        this.untilDate = untilDate;
    }

    public int getRecurrenceId() {
        return recurrenceId;
    }

    public void setRecurrenceId(int recurrenceId) {
        this.recurrenceId = recurrenceId;
    }

    public int getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public SoftTimePreference getSoftTimePreference() {
        return softTimePreference;
    }

    public String getFrequency() {
        return frequency;
    }

    public LocalDateTime getUntilDate() {
        return untilDate;
    }

    public Duration getDuration() {
        return Duration.between(startTime, endTime);
    }

    public List<Event> buildOccurrences(LocalDateTime endInclusive) {
        List<Event> occurrences = new ArrayList<>();
        if (endInclusive.isBefore(startTime)) {
            return occurrences;
        }

        Duration duration = getDuration();
        int occurrenceIndex = 0;
        LocalDateTime occurrenceStart = occurrenceAt(occurrenceIndex);
        while (!occurrenceStart.isAfter(endInclusive)) {
            occurrences.add(new Event(
                    0,
                    userId,
                    recurrenceId == 0 ? null : recurrenceId,
                    title,
                    occurrenceStart,
                    occurrenceStart.plus(duration),
                    priority,
                    description,
                    location,
                    softTimePreference
            ));
            occurrenceIndex++;
            occurrenceStart = occurrenceAt(occurrenceIndex);
        }
        return occurrences;
    }

    private LocalDateTime occurrenceAt(int occurrenceIndex) {
        return switch (frequency) {
            case WEEKLY -> startTime.plusWeeks(occurrenceIndex);
            case MONTHLY -> startTime.plusMonths(occurrenceIndex);
            default -> startTime.plusDays(occurrenceIndex);
        };
    }
}
