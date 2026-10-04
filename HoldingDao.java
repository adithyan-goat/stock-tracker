package com.stocktracker.db;

import com.stocktracker.model.Holding;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HoldingDao {

    private static final String SELECT =
            "SELECT h.id AS hid, h.quantity, h.buy_price, h.buy_date, h.target_price, h.status, h.sell_price, h.sell_date, "
                    + "c.id, c.symbol, c.name, c.market, c.active "
                    + "FROM holdings h JOIN companies c ON c.id = h.company_id ";

    private static Holding map(ResultSet rs) throws SQLException {
        Date sd = rs.getDate("sell_date");
        double sp = rs.getDouble("sell_price");
        boolean noSell = rs.wasNull();
        return new Holding(rs.getLong("hid"), CompanyDao.map(rs), rs.getInt("quantity"),
                rs.getDouble("buy_price"), rs.getDate("buy_date").toLocalDate(),
                rs.getDouble("target_price"), Holding.Status.valueOf(rs.getString("status")),
                noSell ? null : sp, sd == null ? null : sd.toLocalDate());
    }

    public long insert(Holding h) throws SQLException {
        String sql = "INSERT INTO holdings (company_id, quantity, buy_price, buy_date, target_price, status) VALUES (?,?,?,?,?,'OPEN')";
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, h.company().id());
            ps.setInt(2, h.quantity());
            ps.setDouble(3, h.buyPrice());
            ps.setDate(4, Date.valueOf(h.buyDate()));
            ps.setDouble(5, h.targetPrice());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<Holding> findById(long id) throws SQLException {
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(SELECT + "WHERE h.id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public List<Holding> findByStatus(Holding.Status status) throws SQLException {
        List<Holding> out = new ArrayList<>();
        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE h.status=? ORDER BY c.symbol, h.buy_date, h.id")) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public List<Holding> findOpen() throws SQLException {
        return findByStatus(Holding.Status.OPEN);
    }

    public void markSold(long id, double sellPrice, LocalDate date) throws SQLException {
        String sql = "UPDATE holdings SET status='SOLD', sell_price=?, sell_date=? WHERE id=? AND status='OPEN'";
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, sellPrice);
            ps.setDate(2, Date.valueOf(date));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public void updateTarget(long id, double target) throws SQLException {
        String sql = "UPDATE holdings SET target_price=? WHERE id=? AND status='OPEN'";
        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, target);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}
