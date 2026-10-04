package com.stocktracker;

import com.stocktracker.api.PriceProvider;
import com.stocktracker.api.YahooFinanceProvider;
import com.stocktracker.config.Config;
import com.stocktracker.db.AlertDao;
import com.stocktracker.db.CompanyDao;
import com.stocktracker.db.HoldingDao;
import com.stocktracker.db.PriceDao;
import com.stocktracker.model.Alert;
import com.stocktracker.model.Company;
import com.stocktracker.model.Market;
import com.stocktracker.service.AlertService;
import com.stocktracker.service.HoldingService;
import com.stocktracker.service.PriceUpdateService;
import com.stocktracker.service.SchedulerService;
import com.stocktracker.service.SuggestionService;
import com.stocktracker.service.UpdateResult;
import com.stocktracker.util.CompanyImporter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/** Wires all DAOs and services together. */
public final class AppContext {

    public final Config config;
    public final CompanyDao companyDao = new CompanyDao();
    public final PriceDao priceDao = new PriceDao();
    public final HoldingDao holdingDao = new HoldingDao();
    public final AlertDao alertDao = new AlertDao();
    public final PriceProvider provider = new YahooFinanceProvider();
    public final AlertService alertService;
    public final HoldingService holdingService;
    public final SuggestionService suggestionService;
    public final PriceUpdateService updateService;

    public AppContext(Config config) {
        this.config = config;
        this.alertService = new AlertService(holdingDao, priceDao, alertDao);
        this.holdingService = new HoldingService(holdingDao, priceDao);
        this.suggestionService = new SuggestionService(holdingService);
        this.updateService = new PriceUpdateService(companyDao, priceDao, holdingDao, provider, alertService,
                config.apiThreads(), config.apiDelayMs());
    }

    /** First start: load the bundled list of well known NSE and US companies. */
    public int seedIfEmpty() throws SQLException, IOException {
        if (companyDao.count() > 0) return 0;
        try (InputStream in = AppContext.class.getResourceAsStream("/companies_seed.csv")) {
            if (in == null) return 0;
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                List<Company> list = CompanyImporter.parse(r, Market.NSE);
                companyDao.insertBatch(list);
                return list.size();
            }
        }
    }

    public SchedulerService createScheduler(Runnable job) {
        return new SchedulerService(config.zone(), config.updateTimes(), job);
    }

    /** The job run by the scheduler, by "Update now" and by --update-once. */
    public UpdateResult runUpdate(Consumer<String> progress, Consumer<List<Alert>> onAlerts) throws SQLException {
        return updateService.updateAll(progress, onAlerts);
    }
}
