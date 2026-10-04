package com.stocktracker.service;

import java.util.ArrayList;
import java.util.List;

public final class UpdateResult {
    public int total;
    public int ok;
    public int failed;
    public long rows;
    public boolean skipped;
    public final List<String> errors = new ArrayList<>();

    public static UpdateResult alreadyRunning() {
        UpdateResult r = new UpdateResult();
        r.skipped = true;
        return r;
    }

    public String summary() {
        if (skipped) return "An update is already running.";
        return String.format("Update finished: %d companies checked, %d ok, %d failed, %d price rows saved.",
                total, ok, failed, rows);
    }
}
