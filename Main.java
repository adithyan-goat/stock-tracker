package com.stocktracker;

import com.stocktracker.config.Config;
import com.stocktracker.db.Database;
import com.stocktracker.ui.ConsoleApp;
import com.stocktracker.ui.DaemonRunner;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * Entry point.
 * <pre>
 *   java -jar stock-tracker.jar                 interactive console menu
 *   java -jar stock-tracker.jar --daemon        headless: keeps fetching prices at the scheduled times
 *   java -jar stock-tracker.jar --update-once   fetch once and exit (for cron / Windows Task Scheduler)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        String mode = args.length > 0 ? args[0] : "";

        if (mode.equals("--help") || mode.equals("-h")) {
            System.out.println("Usage: java -jar stock-tracker.jar [--daemon | --update-once]");
            return;
        }

        try {
            Config cfg = Config.load();
            Database.init(cfg);
            AppContext ctx = new AppContext(cfg);

            int seeded = ctx.seedIfEmpty();
            if (seeded > 0) System.out.println("First start: loaded " + seeded + " starter companies (NSE + US).");

            switch (mode) {
                case "--daemon" -> new DaemonRunner(ctx).run();
                case "--update-once" -> new DaemonRunner(ctx).runOnce();
                default -> new ConsoleApp(ctx).run();
            }
        } catch (SQLException e) {
            System.err.println("Could not use the MySQL database: " + e.getMessage());
            System.err.println("Check that MySQL is running and that db.url / db.user / db.password in config.properties are correct.");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Startup failed: " + e);
            System.exit(1);
        }
    }
}
