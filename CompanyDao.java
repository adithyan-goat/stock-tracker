package com.stocktracker.db;

import com.stocktracker.model.Company;
import com.stocktracker.model.Market;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CompanyDao {

    static Company map(ResultSet rs) throws SQLException {
        return new Company(rs.getLong("id"), rs.getString("symbol"), rs.getString("name"),
                Market.valueOf(rs.getString("market")), rs.getBoolean("active"));
    }

    public long insert(Company c) throws SQLException {
        String sql = "INSERT INTO companies (symbol, name, market) VALUES (?,?,?)";
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.symbol());
            ps.setString(2, c.name());
            ps.setString(3, c.market().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** Bulk insert used by the CSV importer; companies that already exist are skipped. */
    public void insertBatch(List<Company> list) throws SQLException {
        String sql = "INSERT IGNORE INTO companies (symbol, name, market) VALUES (?,?,?)";
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            con.setAutoCommit(false);
            int n = 0;
            for (Company c : list) {
                ps.setString(1, c.symbol());
                ps.setString(2, c.name());
                ps.setString(3, c.market().name());
                ps.addBatch();
                if (++n % 1000 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            con.commit();
        }
    }

    public int count() throws SQLException {
        try (Connection con = Database.get();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM companies")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public Optional<Company> find(String symbol, Market market) throws SQLException {
        String sql = "SELECT * FROM companies WHERE symbol=? AND market=?";
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, symbol);
            ps.setString(2, market.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Company> findActive() throws SQLException {
        List<Company> out = new ArrayList<>();
        try (Connection con = Database.get();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM companies WHERE active=TRUE ORDER BY id")) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    /** Symbol prefix or any part of the company name. Exact symbol matches come first. */
    public List<Company> search(String text, int limit) throws SQLException {
        String sql = "SELECT * FROM companies WHERE active=TRUE AND (symbol LIKE ? OR name LIKE ?) "
                + "ORDER BY (symbol = ?) DESC, symbol LIMIT ?";
        List<Company> out = new ArrayList<>();
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, text + "%");
            ps.setString(2, "%" + text + "%");
            ps.setString(3, text);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }
}
