package com.example.demo.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecurringEventSeriesTest {

    @Test
    void buildOccurrencesGeneratesDailySeriesWithinInclusiveLimit() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 20, 5, 0);
        RecurringEventSeries series = new RecurringEventSeries(
                42,
                "Daily Football",
                start,
                start.plusHours(1),
                3,
                "",
                "",
                RecurringEventSeries.DAILY,
                start.plusDays(2)
        );

        List<Event> occurrences = series.buildOccurrences(start.plusDays(2));

        assertEquals(3, occurrences.size());
        assertEquals(start, occurrences.get(0).getStartTime());
        assertEquals(start.plusDays(1), occurrences.get(1).getStartTime());
        assertEquals(start.plusDays(2), occurrences.get(2).getStartTime());
        assertEquals(42, occurrences.get(0).getRecurrenceId());
    }

    @Test
    void buildOccurrencesGeneratesWeeklySeries() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 20, 18, 30);
        RecurringEventSeries series = new RecurringEventSeries(
                7,
                "Weekly Review",
                start,
                start.plusHours(2),
                4,
                "",
                "",
                RecurringEventSeries.WEEKLY,
                start.plusWeeks(2)
        );

        List<Event> occurrences = series.buildOccurrences(start.plusWeeks(2));

        assertEquals(3, occurrences.size());
        assertEquals(start, occurrences.get(0).getStartTime());
        assertEquals(start.plusWeeks(1), occurrences.get(1).getStartTime());
        assertEquals(start.plusWeeks(2), occurrences.get(2).getStartTime());
    }

    @Test
    void buildOccurrencesGeneratesMonthlySeries() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 31, 9, 0);
        RecurringEventSeries series = new RecurringEventSeries(
                9,
                "Month End",
                start,
                start.plusMinutes(30),
                2,
                "",
                "",
                RecurringEventSeries.MONTHLY,
                start.plusMonths(2)
        );

        List<Event> occurrences = series.buildOccurrences(start.plusMonths(2));

        assertEquals(3, occurrences.size());
        assertEquals(LocalDateTime.of(2026, 1, 31, 9, 0), occurrences.get(0).getStartTime());
        assertEquals(LocalDateTime.of(2026, 2, 28, 9, 0), occurrences.get(1).getStartTime());
        assertEquals(LocalDateTime.of(2026, 3, 31, 9, 0), occurrences.get(2).getStartTime());
    }
}
