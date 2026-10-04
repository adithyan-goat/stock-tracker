package com.stocktracker.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulingTest {

    private final ZoneId ist = ZoneId.of("Asia/Kolkata");
    private final List<LocalTime> times = List.of(LocalTime.of(18, 30), LocalTime.of(7, 30));

    @Test
    void picksTheNextTimeLaterToday() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, ist);
        assertEquals(ZonedDateTime.of(2026, 10, 4, 18, 30, 0, 0, ist), SchedulerService.computeNext(now, times));
    }

    @Test
    void rollsOverToTomorrowMorningAfterTheLastTime() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 4, 20, 42, 0, 0, ist);
        assertEquals(ZonedDateTime.of(2026, 10, 5, 7, 30, 0, 0, ist), SchedulerService.computeNext(now, times));
    }

    @Test
    void rangeGrowsWithTheGapSinceTheLastStoredDay() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        assertEquals("6mo", PriceUpdateService.chooseRange(null, today));
        assertEquals("5d", PriceUpdateService.chooseRange(LocalDate.of(2026, 10, 3), today));
        assertEquals("1mo", PriceUpdateService.chooseRange(LocalDate.of(2026, 9, 20), today));
        assertEquals("6mo", PriceUpdateService.chooseRange(LocalDate.of(2026, 8, 1), today));
        assertEquals("1y", PriceUpdateService.chooseRange(LocalDate.of(2026, 4, 1), today));
    }
}
