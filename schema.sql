-- Stock Tracker schema (executed automatically at start-up, safe to re-run)

-- Every company that can be tracked (thousands of rows are fine)
CREATE TABLE IF NOT EXISTS companies (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol  VARCHAR(30)  NOT NULL,
  name    VARCHAR(200) NOT NULL,
  market  VARCHAR(5)   NOT NULL,              -- NSE, BSE or US
  active  BOOLEAN      NOT NULL DEFAULT TRUE,
  UNIQUE KEY uq_company (symbol, market),
  KEY idx_company_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per company per trading day; the chart is drawn from this table
CREATE TABLE IF NOT EXISTS daily_prices (
  company_id  BIGINT        NOT NULL,
  trade_date  DATE          NOT NULL,
  open_price  DECIMAL(16,4) NOT NULL,
  high_price  DECIMAL(16,4) NOT NULL,
  low_price   DECIMAL(16,4) NOT NULL,
  close_price DECIMAL(16,4) NOT NULL,
  volume      BIGINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (company_id, trade_date),
  CONSTRAINT fk_price_company FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every purchase is its own row (lot). target_price must be above buy_price.
CREATE TABLE IF NOT EXISTS holdings (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  company_id   BIGINT        NOT NULL,
  quantity     INT           NOT NULL,
  buy_price    DECIMAL(16,4) NOT NULL,
  buy_date     DATE          NOT NULL,
  target_price DECIMAL(16,4) NOT NULL,
  status       VARCHAR(6)    NOT NULL DEFAULT 'OPEN',   -- OPEN or SOLD
  sell_price   DECIMAL(16,4) NULL,
  sell_date    DATE          NULL,
  created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_holding_status (status),
  CONSTRAINT fk_holding_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT chk_holding_qty    CHECK (quantity > 0),
  CONSTRAINT chk_holding_target CHECK (target_price > buy_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Alerts raised by the daily check. The unique key stops duplicates for the same day.
CREATE TABLE IF NOT EXISTS alerts (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  holding_id BIGINT        NOT NULL,
  company_id BIGINT        NOT NULL,
  alert_type VARCHAR(20)   NOT NULL,   -- DAILY_DROP, BELOW_BUY_PRICE, TARGET_REACHED
  trade_date DATE          NOT NULL,
  message    VARCHAR(1000) NOT NULL,
  created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_read    BOOLEAN       NOT NULL DEFAULT FALSE,
  UNIQUE KEY uq_alert (holding_id, alert_type, trade_date),
  CONSTRAINT fk_alert_holding FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE CASCADE,
  CONSTRAINT fk_alert_company FOREIGN KEY (company_id) REFERENCES companies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
