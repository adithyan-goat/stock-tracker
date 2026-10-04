package com.stocktracker.config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Reads config.properties from the working directory; environment variables override any key. */
public final class Config {

    private final Properties props = new Properties();

    private Config() {
    }

    public static Config load() {
        Config c = new Config();
        Path file = Paths.get("config.properties");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                c.props.load(r);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read config.properties", e);
            }
        }
        return c;
    }

    public String get(String key, String def) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env.trim();
        String v = props.getProperty(key);
        return v == null ? def : v.trim();
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public String dbUrl() {
        return get("db.url", "jdbc:mysql://localhost:3306/stock_tracker?createDatabaseIfNotExist=true"
                + "&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true&characterEncoding=utf8");
    }

    public String dbUser() {
        return get("db.user", "root");
    }

    public String dbPassword() {
        return get("db.password", "");
    }

    public ZoneId zone() {
        try {
            return ZoneId.of(get("scheduler.zone", "Asia/Kolkata"));
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    public List<LocalTime> updateTimes() {
        List<LocalTime> times = new ArrayList<>();
        for (String part : get("scheduler.times", "18:30,07:30").split(",")) {
            if (part.isBlank()) continue;
            try {
                times.add(LocalTime.parse(part.trim()));
            } catch (Exception ignored) {
                // skip invalid entries
            }
        }
        if (times.isEmpty()) times.add(LocalTime.of(18, 30));
        return times;
    }

    public boolean updateOnStartup() {
        return getBool("scheduler.update.on.startup", true);
    }

    public int apiThreads() {
        return Math.max(1, getInt("api.threads", 6));
    }

    public long apiDelayMs() {
        return Math.max(0, getInt("api.delay.ms", 150));
    }

    public Path chartsDir() {
        return Paths.get(get("charts.dir", "charts"));
    }
}
