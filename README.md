# Stock Price Tracker (Java console + MySQL)

A console application that **tracks the daily closing price of thousands of companies** (NSE, BSE and US),
draws a price graph for any company, lets you record what you bought and your **target selling price**, and
**alerts you every day** when a stock you own drops, falls below your buying price, or reaches your target.
It does **not** predict prices - it only tracks and alerts.

![sample chart](docs/sample-chart.png)

## Features

| Requirement | How it is done |
|---|---|
| Fetch prices automatically from an API | `YahooFinanceProvider` (no API key). Replaceable through the `PriceProvider` interface |
| Thousands of companies | `companies` table + CSV importer (NSE `EQUITY_L.csv`, NASDAQ `nasdaqlisted.txt`, or your own file); parallel fetching |
| NSE + BSE + US | `Market` enum (`.NS`, `.BO` and plain US tickers) |
| Today's price added to the graph automatically | A scheduler fetches at the times in `config.properties`; the graph is always drawn from the database. `--daemon` / `--update-once` modes work even when the menu is not open; missing days are back-filled at start-up |
| Graph in a console app | ASCII line chart in the terminal **and** a PNG chart export (menu 2 and 3) |
| Mark buying price and selling price | Menu 4. **Target selling price must be higher than the buying price** - checked in the program and by a `CHECK` constraint in MySQL |
| Keep previously bought stocks | `holdings` table. Each purchase is its **own lot** (quantity, buy price, buy date, target) |
| Alert on any decrease vs previous day | `DAILY_DROP` alert (any decrease, per lot, with your unrealised P/L) |
| Suggestion for stocks below buying price | `BELOW_BUY_PRICE` alert plus menu 7 (alert with a buy-more hint showing the new average cost; informational only) |
| Notify when price reaches the target | `TARGET_REACHED` alert with the **profit for your quantity** |

## Requirements

* Java 17 or newer (JDK)
* Maven 3.8+
* MySQL 8.0.16+ (older versions run, but ignore the `CHECK` constraint)
* Internet access (for the price API)

## Setup

1. **Install and start MySQL**, then create a user (or just use `root`):
   ```sql
   CREATE USER 'tracker'@'localhost' IDENTIFIED BY 'your_password';
   GRANT ALL PRIVILEGES ON stock_tracker.* TO 'tracker'@'localhost';
   ```
   The database `stock_tracker` and all tables are **created automatically** on first start
   (`src/main/resources/schema.sql`).
2. **Edit `config.properties`** - at least `db.user` and `db.password`.
3. **Build** (also runs the unit tests):
   ```
   mvn clean package
   ```
   This creates `target/stock-tracker.jar` (a single runnable jar that includes the MySQL driver).
4. **Run** from the project folder (so it finds `config.properties`):
   ```
   java -jar target/stock-tracker.jar
   ```

On the first start about 80 well known NSE and US companies are loaded and their 6-month history is downloaded in the
background, so graphs work immediately. A few symbols may fail if a company was renamed or delisted - they are listed
after the update and can simply be ignored or corrected.

> **First thing to check:** choose menu **8**, add `AAPL` (US). If it reports "added with N days of history", the
> API connection works. If you see HTTP errors, see *Troubleshooting* below.

## Running without opening the menu

| Command | What it does |
|---|---|
| `java -jar target/stock-tracker.jar` | Interactive menu. The scheduler also runs inside it |
| `java -jar target/stock-tracker.jar --daemon` | Headless, keeps running and fetches at the scheduled times, printing alerts to the log |
| `java -jar target/stock-tracker.jar --update-once` | One update, then exit - use with the OS scheduler |

Windows Task Scheduler / Linux cron example (every day 18:30):
```
30 18 * * *  cd /path/to/stock-tracker && java -jar target/stock-tracker.jar --update-once >> update.log 2>&1
```
Alerts are stored in the database, so they are shown the next time you open the menu even if nobody was watching.

## Menu guide

```
 1. Portfolio dashboard          holdings, day change, profit/loss, status per lot
 2. View price graph             ASCII chart with your buy (---) and target (===) lines
 3. Export price graph as PNG    saved in the charts/ folder
 4. Buy a stock                  quantity, buying price, date, target selling price
 5. Sell a holding               records the real selling price, shows realised profit/loss
 6. Alerts                       unread / history
 7. Buy-more suggestions         holdings now trading below the buying price
 8. Add a company                by ticker, history is fetched immediately
 9. Import companies from CSV    thousands at once
10. Update prices now           runs in the background
11. Change target selling price  must stay above the buying price
```

### Importing thousands of companies

Menu 9 accepts:
* `symbol,name,market` (your own file; `market` = NSE, BSE or US)
* NSE's `EQUITY_L.csv` (list of all listed NSE equities - only `EQ`/`BE` series are imported)
* NASDAQ's `nasdaqlisted.txt` / `otherlisted.txt` (test issues are skipped)

Choose the default market when the file has no market column. After the import the history of all new companies is
downloaded in the background (a few minutes for thousands).

## How the alerts work

For every **open lot**, after each update the latest close is compared with the previous day's close:

| Alert | Condition | Message contains |
|---|---|---|
| `PRICE DROP` | today's close < previous close (any decrease) | % fall and your lot's unrealised P/L |
| `BELOW BUY PRICE` | today's close < your buying price | % below, loss, and the average cost if you bought the same quantity again |
| `TARGET REACHED` | today's close >= your target | **profit = (price - buy price) x quantity** |

Each alert is stored once per lot, type and trading day. While a stock stays above the target (or below the buy price) you
get a fresh alert each new trading day until you sell the lot.

> Run the update after market close. A run during market hours stores the live price for today, which is replaced by
> the final close at the next run (but an alert already raised for that day is not repeated).

## Project structure

```
stock-tracker/
├── pom.xml                      Maven build (Gson, MySQL driver, JUnit, shaded jar)
├── config.properties            database, schedule, API threads, chart folder
├── src/main/resources/
│   ├── schema.sql               database tables (auto-created)
│   └── companies_seed.csv       starter list of companies
└── src/main/java/com/stocktracker/
    ├── Main.java                entry point (menu / --daemon / --update-once)
    ├── AppContext.java          wires DAOs and services together
    ├── config/Config.java       reads config.properties
    ├── model/                   Company, DailyPrice, Holding, Position, Alert, Market ...
    ├── db/                      Database + CompanyDao, PriceDao, HoldingDao, AlertDao (plain JDBC)
    ├── api/                     PriceProvider + YahooFinanceProvider
    ├── service/                 PriceUpdateService, AlertEvaluator (rules), AlertService,
    │                            HoldingService, SuggestionService, SchedulerService
    ├── chart/                   AsciiChart, ChartImageExporter (Java2D PNG), ChartMarker
    ├── ui/                      ConsoleApp (menus), ConsoleNotifier, DaemonRunner
    └── util/                    CompanyImporter, Fmt
```

### Database design

```
companies (id, symbol, name, market, active)            UNIQUE(symbol, market)
daily_prices (company_id, trade_date, open, high, low, close, volume)   PK(company_id, trade_date)
holdings (id, company_id, quantity, buy_price, buy_date, target_price,
          status OPEN/SOLD, sell_price, sell_date)       CHECK(target_price > buy_price)
alerts (id, holding_id, company_id, alert_type, trade_date, message, created_at, is_read)
                                                         UNIQUE(holding_id, alert_type, trade_date)
```

## Troubleshooting

* **"Could not use the MySQL database"** - MySQL is not running, or `db.user` / `db.password` in `config.properties` is wrong.
* **Many "HTTP 429" / "rate limited" failures** - lower `api.threads` or raise `api.delay.ms`, then run *Update prices now* again.
* **"symbol not found at API"** - wrong symbol/market (NSE and BSE symbols differ, e.g. `M&M` on NSE) or the company was renamed or delisted.
* **API stops working** - the Yahoo endpoint is unofficial. Write another class implementing `PriceProvider`
  (for example for Alpha Vantage or Twelve Data) and change one line in `AppContext`.
* **Rupee sign** - amounts are printed as `Rs` and `$` so they display correctly on every console.

## Notes and limitations

* Single user, no login (a login table could be added later).
* Prices are daily closes; there is no intraday tracking.
* Suggestions are rule based and informational only - **not financial advice**.
