package com.stocktracker.ui;

import com.stocktracker.AppContext;
import com.stocktracker.service.SchedulerService;
import com.stocktracker.service.UpdateResult;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;

/**
 * Headless modes so prices keep being collected even when nobody opens the console menu:
 * --daemon (keeps running and fetches at the scheduled times) and --update-once (single run, for cron / Task Scheduler).
 */
public final class DaemonRunner {

    private final AppContext ctx;

    public DaemonRunner(AppContext ctx) {
        this.ctx = ctx;
    }

    public void runOnce() {
        try {
            UpdateResult r = ctx.runUpdate(DaemonRunner::log, ConsoleNotifier::print);
            log(r.summary());
            r.errors.forEach(e -> log("  failed: " + e));
        } catch (SQLException e) {
            log("Database error: " + e.getMessage());
        }
    }

    public void run() {
        SchedulerService scheduler = ctx.createScheduler(this::runOnce);
        scheduler.start();
        log("Daemon started. Next scheduled update: " + scheduler.nextRun());
        if (ctx.config.updateOnStartup()) {
            Thread t = new Thread(this::runOnce, "startup-update");
            t.setDaemon(true);
            t.start();
        }
        try {
            new CountDownLatch(1).await();   // keep running until the process is stopped (Ctrl+C)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(String msg) {
        System.out.println("[" + LocalDateTime.now().withNano(0) + "] " + msg);
    }
}
