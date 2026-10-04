package com.stocktracker.service;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Runs the update job every day at the configured times. */
public final class SchedulerService implements AutoCloseable {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "daily-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ZoneId zone;
    private final List<LocalTime> times;
    private final Runnable job;
    private volatile ZonedDateTime nextRun;

    public SchedulerService(ZoneId zone, List<LocalTime> times, Runnable job) {
        this.zone = zone;
        this.times = times;
        this.job = job;
    }

    public void start() {
        scheduleNext();
    }

    public ZonedDateTime nextRun() {
        return nextRun;
    }

    /** The next configured time that is still in the future. */
    static ZonedDateTime computeNext(ZonedDateTime now, List<LocalTime> times) {
        ZonedDateTime best = null;
        for (LocalTime t : times) {
            ZonedDateTime c = now.with(t);
            if (!c.isAfter(now)) c = c.plusDays(1);
            if (best == null || c.isBefore(best)) best = c;
        }
        return best;
    }

    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = computeNext(now, times);
        nextRun = next;
        long delay = Math.max(1000, Duration.between(now, next).toMillis());
        executor.schedule(() -> {
            try {
                job.run();
            } catch (Throwable t) {
                System.err.println("Scheduled update failed: " + t);
            } finally {
                scheduleNext();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
