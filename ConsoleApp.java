package com.stocktracker.ui;

import com.stocktracker.AppContext;
import com.stocktracker.chart.AsciiChart;
import com.stocktracker.chart.ChartImageExporter;
import com.stocktracker.chart.ChartMarker;
import com.stocktracker.model.Alert;
import com.stocktracker.model.Company;
import com.stocktracker.model.DailyPrice;
import com.stocktracker.model.Holding;
import com.stocktracker.model.Market;
import com.stocktracker.model.Position;
import com.stocktracker.service.SchedulerService;
import com.stocktracker.service.SuggestionService;
import com.stocktracker.service.UpdateResult;
import com.stocktracker.util.CompanyImporter;
import com.stocktracker.util.Fmt;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ConsoleApp {

    /** Thrown when the input stream is closed (Ctrl+D / Ctrl+Z) so the program can exit cleanly. */
    private static final class InputClosed extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final AppContext ctx;
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "price-updater");
        t.setDaemon(true);
        return t;
    });
    private SchedulerService scheduler;

    public ConsoleApp(AppContext ctx) {
        this.ctx = ctx;
    }

    // ------------------------------------------------------------------ main loop

    public void run() {
        System.out.println();
        System.out.println("=====================================================");
        System.out.println("   STOCK PRICE TRACKER  (NSE / BSE / US)");
        System.out.println("=====================================================");
        try {
            showUnreadAlertsOnStart();

            scheduler = ctx.createScheduler(this::scheduledJob);
            scheduler.start();
            System.out.println("Automatic updates run daily at " + ctx.config.updateTimes() + " ("
                    + ctx.config.zone() + "). Next run: " + scheduler.nextRun().toLocalDateTime().withNano(0));
            if (ctx.config.updateOnStartup()) {
                System.out.println("Catching up on any missing days in the background...");
                startBackgroundUpdate();
            }

            while (true) {
                printMenu();
                String choice = ask("Choose: ");
                try {
                    switch (choice) {
                        case "1" -> dashboard();
                        case "2" -> showGraph();
                        case "3" -> exportGraph();
                        case "4" -> buyStock();
                        case "5" -> sellStock();
                        case "6" -> alertsMenu();
                        case "7" -> suggestions();
                        case "8" -> addCompany();
                        case "9" -> importCompanies();
                        case "10" -> startBackgroundUpdate();
                        case "11" -> editTarget();
                        case "0" -> {
                            System.out.println("Goodbye. (Prices keep being collected if you run the program with --daemon.)");
                            return;
                        }
                        default -> System.out.println("Please choose a number from the menu.");
                    }
                } catch (SQLException e) {
                    System.out.println("Database error: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("Not saved: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Error: " + e.getMessage());
                }
            }
        } catch (InputClosed e) {
            System.out.println();
        } catch (SQLException e) {
            System.out.println("Fatal error: " + e.getMessage());
        } finally {
            if (scheduler != null) scheduler.close();
            background.shutdownNow();
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("------------------------ MENU ------------------------");
        System.out.println("  1. Portfolio dashboard (my holdings, profit / loss)");
        System.out.println("  2. View price graph in the console");
        System.out.println("  3. Export price graph as a PNG picture");
        System.out.println("  4. Buy a stock (record purchase + target selling price)");
        System.out.println("  5. Sell a holding");
        System.out.println("  6. Alerts");
        System.out.println("  7. Buy-more suggestions (stocks below my buying price)");
        System.out.println("  8. Add a company");
        System.out.println("  9. Import companies from a CSV file");
        System.out.println(" 10. Update prices now");
        System.out.println(" 11. Change target selling price of a holding");
        System.out.println("  0. Exit");
        System.out.println("------------------------------------------------------");
    }

    // ------------------------------------------------------------------ updates

    private void scheduledJob() {
        try {
            System.out.println("\n[Scheduled update started]");
            UpdateResult r = ctx.runUpdate(System.out::println, ConsoleNotifier::print);
            System.out.println(r.summary());
        } catch (SQLException e) {
            System.out.println("Scheduled update failed: " + e.getMessage());
        }
    }

    private void startBackgroundUpdate() {
        if (ctx.updateService.isRunning()) {
            System.out.println("An update is already running in the background.");
            return;
        }
        background.submit(() -> {
            try {
                UpdateResult r = ctx.runUpdate(System.out::println, ConsoleNotifier::print);
                System.out.println(r.summary());
                for (String e : r.errors.subList(0, Math.min(5, r.errors.size()))) System.out.println("   failed: " + e);
                if (r.errors.size() > 5) System.out.println("   ... and more (check symbols / network).");
            } catch (SQLException e) {
                System.out.println("Update failed: " + e.getMessage());
            }
        });
        System.out.println("Price update started in the background - you can keep using the menu.");
    }

    private void showUnreadAlertsOnStart() throws SQLException {
        List<Alert> unread = ctx.alertDao.findUnread();
        if (unread.isEmpty()) {
            System.out.println("No unread alerts.");
        } else {
            ConsoleNotifier.print(unread);
            ctx.alertDao.markAllRead();
        }
    }

    // ------------------------------------------------------------------ 1. dashboard

    private void dashboard() throws SQLException {
        List<Position> positions = ctx.holdingService.positions();
        if (positions.isEmpty()) {
            System.out.println("You have no open holdings yet. Use menu 4 to record a purchase.");
        } else {
            System.out.println();
            System.out.printf("%-4s %-13s %-4s %5s %12s %12s %12s %8s %14s %8s  %s%n",
                    "Lot", "Symbol", "Mkt", "Qty", "Buy", "Target", "Last", "Day", "P/L", "P/L %", "Status");
            Map<String, double[]> totals = new LinkedHashMap<>();   // currency -> invested, value
            for (Position p : positions) {
                Holding h = p.lot();
                Market m = h.company().market();
                String last = p.hasPrice() ? Fmt.money(m, p.price()) : "n/a";
                String day = p.dayChangePct() == null ? "-" : Fmt.pct(p.dayChangePct());
                String pl = p.hasPrice() ? Fmt.signedMoney(m, p.unrealisedPl()) : "-";
                String plPct = p.hasPrice() ? Fmt.pct(p.unrealisedPct()) : "-";
                System.out.printf("%-4d %-13s %-4s %5d %12s %12s %12s %8s %14s %8s  %s%n",
                        h.id(), Fmt.cut(h.company().symbol(), 13), m, h.quantity(), Fmt.money(m, h.buyPrice()),
                        Fmt.money(m, h.targetPrice()), last, day, pl, plPct, statusText(p.status()));
                if (p.hasPrice()) {
                    double[] t = totals.computeIfAbsent(m.currency, k -> new double[2]);
                    t[0] += h.invested();
                    t[1] += p.currentValue();
                }
            }
            System.out.println();
            for (Map.Entry<String, double[]> e : totals.entrySet()) {
                Market m = e.getKey().equals("USD") ? Market.US : Market.NSE;
                double inv = e.getValue()[0], val = e.getValue()[1];
                System.out.println(e.getKey() + ": invested " + Fmt.money(m, inv) + " | current value " + Fmt.money(m, val)
                        + " | unrealised P/L " + Fmt.signedMoney(m, val - inv) + " (" + Fmt.pct((val - inv) / inv * 100.0) + ")");
            }
        }

        List<Holding> sold = ctx.holdingService.soldLots();
        if (!sold.isEmpty()) {
            Map<String, Double> realised = new LinkedHashMap<>();
            for (Holding h : sold) realised.merge(h.company().market().currency, h.realisedPl(), Double::sum);
            for (Map.Entry<String, Double> e : realised.entrySet()) {
                Market m = e.getKey().equals("USD") ? Market.US : Market.NSE;
                System.out.println("Realised profit/loss from sold lots (" + e.getKey() + "): " + Fmt.signedMoney(m, e.getValue()));
            }
        }
    }

    private static String statusText(Position.Status s) {
        return switch (s) {
            case TARGET_HIT -> "TARGET HIT - consider selling";
            case BELOW_BUY -> "BELOW BUY PRICE";
            case OK -> "ok";
            case NO_DATA -> "no price data yet";
        };
    }

    // ------------------------------------------------------------------ 2 + 3. graphs

    private List<ChartMarker> markersFor(Company c) throws SQLException {
        List<ChartMarker> markers = new ArrayList<>();
        for (Position p : ctx.holdingService.positions()) {
            if (p.lot().company().id() != c.id()) continue;
            markers.add(new ChartMarker(ChartMarker.Kind.BUY, p.lot().buyPrice(), "Buy price lot #" + p.lot().id()));
            markers.add(new ChartMarker(ChartMarker.Kind.TARGET, p.lot().targetPrice(), "Target lot #" + p.lot().id()));
        }
        return markers;
    }

    private void showGraph() throws SQLException {
        Company c = pickCompany();
        if (c == null) return;
        int days = askInt("How many days to show? [60]: ", 5, 3650, 60);
        List<DailyPrice> data = ctx.priceDao.history(c.id(), days);
        String title = "=== " + c.symbol() + " (" + c.market() + ") " + c.name() + " | last " + days + " days ===";
        System.out.println();
        System.out.println(AsciiChart.render(title, data, 18, 90, markersFor(c)));
    }

    private void exportGraph() throws SQLException, IOException {
        Company c = pickCompany();
        if (c == null) return;
        int days = askInt("How many days to include? [180]: ", 5, 3650, 180);
        List<DailyPrice> data = ctx.priceDao.history(c.id(), days);
        Path file = ChartImageExporter.export(c, data, markersFor(c), ctx.config.chartsDir());
        System.out.println("Chart saved: " + file.toAbsolutePath());
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
            }
        } catch (Exception ignored) {
            // opening the picture is only a convenience
        }
    }

    // ------------------------------------------------------------------ 4. buy

    private void buyStock() throws SQLException {
        Company c = pickCompany();
        if (c == null) return;
        Market m = c.market();

        List<DailyPrice> last = ctx.priceDao.latest(c.id(), 1);
        Double lastClose = last.isEmpty() ? null : last.get(0).close();
        if (lastClose != null) {
            System.out.println("Latest stored close (" + last.get(0).date() + "): " + Fmt.money(m, lastClose));
        } else {
            System.out.println("No stored price for this company yet (run menu 10).");
        }

        int qty = askInt("Quantity bought (0 = cancel): ", 0, 100_000_000, null);
        if (qty == 0) return;
        double buy = askDouble("Buying price per share" + defText(m, lastClose) + ": ", lastClose);
        LocalDate date = askDate("Buying date [today]: ", LocalDate.now());
        System.out.println("The selling target must be higher than " + Fmt.money(m, buy) + ".");
        double target = askDouble("Target selling price per share: ", null);

        Holding h = ctx.holdingService.addPurchase(c, qty, buy, date, target);   // validates target > buy
        System.out.println("Saved lot #" + h.id() + ": " + qty + " x " + c.symbol() + " @ " + Fmt.money(m, buy)
                + ", target " + Fmt.money(m, target) + " (profit at target: " + Fmt.signedMoney(m, h.targetProfit()) + ").");
        ConsoleNotifier.print(ctx.alertService.evaluateAll());
    }

    private static String defText(Market m, Double def) {
        return def == null ? "" : " [" + Fmt.num(def) + "]";
    }

    // ------------------------------------------------------------------ 5. sell

    private void sellStock() throws SQLException {
        List<Position> open = ctx.holdingService.positions();
        if (open.isEmpty()) {
            System.out.println("You have no open holdings.");
            return;
        }
        listLots(open);
        long id = askInt("Lot number to sell (0 = cancel): ", 0, Integer.MAX_VALUE, null);
        if (id == 0) return;
        Position p = open.stream().filter(x -> x.lot().id() == id).findFirst().orElse(null);
        if (p == null) {
            System.out.println("No open lot with that number.");
            return;
        }
        Holding h = p.lot();
        Market m = h.company().market();
        Double def = p.hasPrice() ? p.price() : null;
        double sellPrice = askDouble("Selling price per share" + defText(m, def) + ": ", def);
        if (sellPrice <= h.buyPrice()) {
            System.out.println("Warning: this price is not above your buying price (" + Fmt.money(m, h.buyPrice())
                    + "); the sale would not make a profit.");
            if (!ask("Continue anyway? (y/N): ").equalsIgnoreCase("y")) return;
        }
        LocalDate date = askDate("Selling date [today]: ", LocalDate.now());
        Holding sold = ctx.holdingService.sell(h.id(), sellPrice, date);
        System.out.println("Lot #" + sold.id() + " sold. Realised profit/loss: " + Fmt.signedMoney(m, sold.realisedPl()));
    }

    private void listLots(List<Position> open) {
        System.out.printf("%-4s %-13s %-4s %5s %12s %12s %12s%n", "Lot", "Symbol", "Mkt", "Qty", "Buy", "Target", "Last");
        for (Position p : open) {
            Holding h = p.lot();
            Market m = h.company().market();
            System.out.printf("%-4d %-13s %-4s %5d %12s %12s %12s%n", h.id(), Fmt.cut(h.company().symbol(), 13), m,
                    h.quantity(), Fmt.money(m, h.buyPrice()), Fmt.money(m, h.targetPrice()),
                    p.hasPrice() ? Fmt.money(m, p.price()) : "n/a");
        }
    }

    // ------------------------------------------------------------------ 11. edit target

    private void editTarget() throws SQLException {
        List<Position> open = ctx.holdingService.positions();
        if (open.isEmpty()) {
            System.out.println("You have no open holdings.");
            return;
        }
        listLots(open);
        long id = askInt("Lot number (0 = cancel): ", 0, Integer.MAX_VALUE, null);
        if (id == 0) return;
        double target = askDouble("New target selling price: ", null);
        ctx.holdingService.changeTarget(id, target);
        System.out.println("Target updated.");
        ConsoleNotifier.print(ctx.alertService.evaluateAll());
    }

    // ------------------------------------------------------------------ 6. alerts

    private void alertsMenu() throws SQLException {
        System.out.println("  1. Unread alerts");
        System.out.println("  2. Last 30 alerts (history)");
        String c = ask("Choose [1]: ");
        if (c.equals("2")) {
            List<Alert> recent = ctx.alertDao.findRecent(30);
            if (recent.isEmpty()) System.out.println("No alerts yet.");
            for (Alert a : recent) {
                System.out.println("[" + a.type().label + "] " + a.tradeDate() + (a.read() ? "" : "  (new)"));
                System.out.println("   " + a.message());
            }
        } else {
            List<Alert> unread = ctx.alertDao.findUnread();
            if (unread.isEmpty()) {
                System.out.println("No unread alerts.");
            } else {
                ConsoleNotifier.print(unread);
                ctx.alertDao.markAllRead();
            }
        }
    }

    // ------------------------------------------------------------------ 7. suggestions

    private void suggestions() throws SQLException {
        List<SuggestionService.Suggestion> list = ctx.suggestionService.belowBuyPrice();
        if (list.isEmpty()) {
            System.out.println("None of your holdings is trading below its buying price right now.");
            return;
        }
        System.out.println("Stocks you own that are now cheaper than your purchase price:");
        for (SuggestionService.Suggestion s : list) {
            Holding h = s.position().lot();
            Market m = h.company().market();
            System.out.printf("- %s lot #%d: bought at %s, now %s (%.2f%% lower). Buying %d more now would lower the average cost to %s.%n",
                    h.company().symbol(), h.id(), Fmt.money(m, h.buyPrice()), Fmt.money(m, s.position().price()),
                    s.belowPct(), h.quantity(), Fmt.money(m, s.avgCostIfSameQtyBought()));
        }
        System.out.println("(Informational only, not financial advice. A falling price can keep falling.)");
    }

    // ------------------------------------------------------------------ 8 + 9. companies

    private void addCompany() throws SQLException {
        System.out.println("  1. NSE (India)   2. BSE (India)   3. US");
        int mk = askInt("Market [1]: ", 1, 3, 1);
        Market market = mk == 1 ? Market.NSE : mk == 2 ? Market.BSE : Market.US;
        String symbol = ask("Ticker symbol (e.g. TCS, RELIANCE, AAPL; blank = cancel): ").toUpperCase();
        if (symbol.isEmpty()) return;

        if (ctx.companyDao.find(symbol, market).isPresent()) {
            System.out.println(symbol + " is already in the database.");
            return;
        }
        String name = ask("Company name (optional): ");
        Company probe = new Company(0, symbol, name.isEmpty() ? symbol : name, market, true);
        long id = ctx.companyDao.insert(probe);
        Company saved = new Company(id, probe.symbol(), probe.name(), market, true);
        try {
            int rows = ctx.updateService.refreshCompany(saved, "6mo");
            if (rows == 0) throw new IOException("no price data returned");
            System.out.println("Added " + saved.label() + " with " + rows + " days of history.");
        } catch (Exception e) {
            System.out.println("Added " + saved.label() + ", but the first price fetch failed: " + e.getMessage());
            System.out.println("Check the symbol/market. It will be retried at the next update.");
        }
    }

    private void importCompanies() throws SQLException, IOException {
        System.out.println("Import a CSV with columns symbol,name,market  (or NSE's EQUITY_L.csv / NASDAQ's nasdaqlisted.txt).");
        String path = ask("File path (blank = cancel): ");
        if (path.isEmpty()) return;
        Path file = Paths.get(path.replace("\"", ""));
        if (!Files.isRegularFile(file)) {
            System.out.println("File not found: " + file.toAbsolutePath());
            return;
        }
        System.out.println("  1. NSE   2. BSE   3. US   (used when the file has no market column)");
        int mk = askInt("Default market [1]: ", 1, 3, 1);
        Market market = mk == 1 ? Market.NSE : mk == 2 ? Market.BSE : Market.US;
        CompanyImporter.Result r = CompanyImporter.importFile(file, market, ctx.companyDao);
        System.out.println("Read " + r.rowsRead() + " rows, added " + r.inserted() + " new companies.");
        if (r.inserted() > 0) {
            System.out.println("Fetching their price history now (thousands of companies can take a few minutes).");
            startBackgroundUpdate();
        }
    }

    // ------------------------------------------------------------------ helpers

    private Company pickCompany() throws SQLException {
        while (true) {
            String q = ask("Search company by symbol or name (blank = cancel): ");
            if (q.isEmpty()) return null;
            List<Company> found = ctx.companyDao.search(q, 15);
            if (found.isEmpty()) {
                System.out.println("No match. Try another word, or add it with menu 8.");
                continue;
            }
            for (int i = 0; i < found.size(); i++) System.out.printf("%2d) %s%n", i + 1, found.get(i).label());
            int n = askInt("Choose number (0 = search again): ", 0, found.size(), found.size() == 1 ? 1 : null);
            if (n > 0) return found.get(n - 1);
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        System.out.flush();
        try {
            String line = in.readLine();
            if (line == null) throw new InputClosed();
            return line.trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int askInt(String prompt, int min, int max, Integer def) {
        while (true) {
            String s = ask(prompt);
            if (s.isEmpty() && def != null) return def;
            try {
                int v = Integer.parseInt(s.replace(",", ""));
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) {
                // fall through to the message
            }
            System.out.println("Please enter a whole number between " + min + " and " + max + ".");
        }
    }

    private double askDouble(String prompt, Double def) {
        while (true) {
            String s = ask(prompt);
            if (s.isEmpty() && def != null) return def;
            try {
                double v = Double.parseDouble(s.replace(",", ""));
                if (v > 0 && !Double.isInfinite(v)) return v;
            } catch (NumberFormatException ignored) {
                // fall through to the message
            }
            System.out.println("Please enter a number greater than zero.");
        }
    }

    private LocalDate askDate(String prompt, LocalDate def) {
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        while (true) {
            String s = ask(prompt);
            if (s.isEmpty()) return def;
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e1) {
                try {
                    return LocalDate.parse(s, dmy);
                } catch (DateTimeParseException e2) {
                    System.out.println("Use yyyy-MM-dd (e.g. 2026-10-04) or dd-MM-yyyy.");
                }
            }
        }
    }
}
