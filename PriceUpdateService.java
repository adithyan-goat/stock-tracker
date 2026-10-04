package com.stocktracker.service;

import com.stocktracker.api.PriceProvider;
import com.stocktracker.db.CompanyDao;
import com.stocktracker.db.HoldingDao;
import com.stocktracker.db.PriceDao;
import com.stocktracker.model.Alert;
import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Fetches prices for every active company and stores them, then runs the alert rules.
 * Companies the user holds are updated first so alerts arrive quickly even when thousands of
 * companies are tracked. Missing days (PC was off) are filled automatically by fetching a longer range.
 */
public final class PriceUpdateService {

    private final CompanyDao companies;
    private final PriceDao prices;
    private final HoldingDao holdings;
    private final PriceProvider provider;
    private final AlertService alertService;
    private final int threads;
    private final long delayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PriceUpdateService(CompanyDao companies, PriceDao prices, HoldingDao holdings, PriceProvider provider,
                              AlertService alertService, int threads, long delayMs) {
        this.companies = companies;
        this.prices = prices;
        this.holdings = holdings;
        this.provider = provider;
        this.alertService = alertService;
        this.threads = threads;
        this.delayMs = delayMs;
    }

    public boolean isRunning() {
        return running.get();
    }

    public UpdateResult updateAll(Consumer<String> progress, Consumer<List<Alert>> onAlerts) throws SQLException {
        if (!running.compareAndSet(false, true)) return UpdateResult.alreadyRunning();
        try {
            List<Company> all = companies.findActive();
            Set<Long> heldIds = holdings.findOpen().stream().map(h -> h.company().id()).collect(Collectors.toSet());
            List<Company> held = new ArrayList<>();
            List<Company> rest = new ArrayList<>();
            for (Company c : all) (heldIds.contains(c.id()) ? held : rest).add(c);

            UpdateResult result = new UpdateResult();
            progress.accept("Price update started: " + all.size() + " companies (" + held.size() + " held first).");

            fetchAndStore(held, result, progress);
            List<Alert> fresh = alertService.evaluateAll();
            if (!fresh.isEmpty()) onAlerts.accept(fresh);

            fetchAndStore(rest, result, progress);
            return result;
        } finally {
            running.set(false);
        }
    }

    /** How far back to ask the API, based on what is already stored. */
    static String chooseRange(LocalDate latest, LocalDate today) {
        if (latest == null) return "6mo";                       // first time: build history for the graph
        long gap = ChronoUnit.DAYS.between(latest, today);
        if (gap > 300) return "5y";
        if (gap > 90) return "1y";
        if (gap > 25) return "6mo";
        if (gap > 4) return "1mo";
        return "5d";
    }

    private record Fetched(Company company, List<DailyPrice> rows, String error) {
    }

    private void fetchAndStore(List<Company> list, UpdateResult res, Consumer<String> progress) throws SQLException {
        if (list.isEmpty()) return;
        Map<Long, LocalDate> latest = prices.latestDates();
        LocalDate today = LocalDate.now();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        ExecutorCompletionService<Fetched> ecs = new ExecutorCompletionService<>(pool);
        try {
            for (Company c : list) {
                String range = chooseRange(latest.get(c.id()), today);
                ecs.submit(() -> fetchOne(c, range));
            }

            List<DailyPrice> buffer = new ArrayList<>();
            for (int i = 1; i <= list.size(); i++) {
                Fetched f;
                try {
                    f = ecs.take().get();
                } catch (ExecutionException e) {
                    res.total++;
                    res.failed++;
                    continue;
                }
                res.total++;
                if (f.error() == null) {
                    res.ok++;
                    buffer.addAll(f.rows());
                } else {
                    res.failed++;
                    if (res.errors.size() < 25) res.errors.add(f.company().symbol() + ": " + f.error());
                }
                if (buffer.size() >= 2000) {
                    res.rows += prices.upsertBatch(buffer);
                    buffer.clear();
                }
                if (i % 100 == 0 && list.size() > 100) progress.accept("  ... " + i + "/" + list.size() + " fetched");
            }
            res.rows += prices.upsertBatch(buffer);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }
    }

    private Fetched fetchOne(Company c, String range) {
        try {
            List<DailyPrice> rows = provider.fetchDaily(c, range);
            if (delayMs > 0) Thread.sleep(delayMs);
            return new Fetched(c, rows, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Fetched(c, List.of(), "interrupted");
        } catch (Exception e) {
            return new Fetched(c, List.of(), e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Fetch one company right away (used when a company is added manually). */
    public int refreshCompany(Company c, String range) throws Exception {
        List<DailyPrice> rows = provider.fetchDaily(c, range);
        return prices.upsertBatch(rows);
    }
}
