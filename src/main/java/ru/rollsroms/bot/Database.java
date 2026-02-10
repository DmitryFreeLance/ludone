package ru.rollsroms.bot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Database {
  private final String dbPath;
  private Connection connection;

  public Database(String dbPath) {
    this.dbPath = dbPath;
  }

  public void init() throws SQLException {
    ensureDirectory();
    connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    try (Statement stmt = connection.createStatement()) {
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS admins (" +
          "user_id INTEGER PRIMARY KEY," +
          "added_at TEXT" +
          ")");
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS orders (" +
          "id INTEGER PRIMARY KEY AUTOINCREMENT," +
          "user_id INTEGER," +
          "tag TEXT," +
          "phone TEXT," +
          "address TEXT," +
          "total INTEGER," +
          "currency TEXT," +
          "created_at TEXT" +
          ")");
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS order_items (" +
          "order_id INTEGER," +
          "product_id TEXT," +
          "title TEXT," +
          "qty INTEGER," +
          "price INTEGER" +
          ")");
    }
  }

  public void ensureAdmins(Set<Long> ids) throws SQLException {
    for (Long id : ids) {
      addAdmin(id);
    }
  }

  public boolean isAdmin(long userId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT user_id FROM admins WHERE user_id = ?")) {
      ps.setLong(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next();
      }
    }
  }

  public void addAdmin(long userId) throws SQLException {
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT OR IGNORE INTO admins (user_id, added_at) VALUES (?, ?)")) {
      ps.setLong(1, userId);
      ps.setString(2, Instant.now().toString());
      ps.executeUpdate();
    }
  }

  public List<Long> listAdmins() throws SQLException {
    List<Long> ids = new ArrayList<>();
    try (PreparedStatement ps = connection.prepareStatement(
        "SELECT user_id FROM admins ORDER BY user_id")) {
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          ids.add(rs.getLong("user_id"));
        }
      }
    }
    return ids;
  }

  public long saveOrder(Order order) throws SQLException {
    long orderId;
    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO orders (user_id, tag, phone, address, total, currency, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, order.userId());
      ps.setString(2, order.tag());
      ps.setString(3, order.phone());
      ps.setString(4, order.address());
      ps.setInt(5, order.total());
      ps.setString(6, order.currency());
      ps.setString(7, Instant.now().toString());
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        rs.next();
        orderId = rs.getLong(1);
      }
    }

    try (PreparedStatement ps = connection.prepareStatement(
        "INSERT INTO order_items (order_id, product_id, title, qty, price) VALUES (?, ?, ?, ?, ?)")) {
      for (OrderItem item : order.items()) {
        ps.setLong(1, orderId);
        ps.setString(2, item.product().id());
        ps.setString(3, item.product().title());
        ps.setInt(4, item.quantity());
        ps.setInt(5, item.unitPrice());
        ps.addBatch();
      }
      ps.executeBatch();
    }
    return orderId;
  }

  private void ensureDirectory() {
    Path path = Path.of(dbPath).toAbsolutePath().getParent();
    if (path == null) {
      return;
    }
    try {
      Files.createDirectories(path);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create DB directory: " + path, e);
    }
  }
}
