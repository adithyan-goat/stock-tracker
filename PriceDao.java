package com.stocktracker.db;

import com.stocktracker.model.DailyPrice;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PriceDao {

    private static DailyPrice map(ResultSet rs) throws SQLException {
        return new DailyPrice(rs.getLong("company_id"), rs.getDate("trade_date").toLocalDate(),
                rs.getDouble("open_price"), rs.getDouble("high_price"), rs.getDouble("low_price"),
                rs.getDouble("close_price"), rs.getLong("volume"));
    }

    /** Inserts new days and refreshes days that already exist (a day that was still open gets its final close). */
    public int upsertBatch(List<DailyPrice> rows) throws SQLException {
        if (rows.isEmpty()) return 0;
        String sql = "INSERT INTO daily_prices (company_id, trade_date, open_price, high_price, low_price, close_price, volume) "
                + "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE open_price=VALUES(open_price), "
                + "high_price=VALUES(high_price), low_price=VALUES(low_price), close_price=VALUES(close_price), "
                + "volume=VALUES(volume)";
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            int n = 0;
            for (DailyPrice p : rows) {
                ps.setLong(1, p.companyId());
                ps.setDate(2, Date.valueOf(p.date()));
                ps.setDouble(3, p.open());
                ps.setDouble(4, p.high());
                ps.setDouble(5, p.low());
                ps.setDouble(6, p.close());
                ps.setLong(7, p.volume());
                ps.addBatch();
                if (++n % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            con.commit();
        }
        return rows.size();
    }

    /** Latest stored day for every company, in a single query. */
    public Map<Long, LocalDate> latestDates() throws SQLException {
        Map<Long, LocalDate> out = new HashMap<>();
        try (Connection con = Database.get();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT company_id, MAX(trade_date) d FROM daily_prices GROUP BY company_id")) {
            while (rs.next()) out.put(rs.getLong(1), rs.getDate(2).toLocalDate());
        }
        return out;
    }

    /** The newest {@code n} days, oldest first. */
    public List<DailyPrice> latest(long companyId, int n) throws SQLException {
        String sql = "SELECT * FROM daily_prices WHERE company_id=? ORDER BY trade_date DESC LIMIT ?";
        List<DailyPrice> out = new ArrayList<>();
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, companyId);
            ps.setInt(2, n);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        Collections.reverse(out);
        return out;
    }

    /** All stored days of the last {@code days} calendar days, oldest first. */
    public List<DailyPrice> history(long companyId, int days) throws SQLException {
        String sql = "SELECT * FROM daily_prices WHERE company_id=? AND trade_date >= ? ORDER BY trade_date";
        List<DailyPrice> out = new ArrayList<>();
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, companyId);
            ps.setDate(2, Date.valueOf(LocalDate.now().minusDays(days)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public LocalDate newestDateOverall() throws SQLException {
        try (Connection con = Database.get();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(trade_date) FROM daily_prices")) {
            rs.next();
            Date d = rs.getDate(1);
            return d == null ? null : d.toLocalDate();
        }
    }
}
