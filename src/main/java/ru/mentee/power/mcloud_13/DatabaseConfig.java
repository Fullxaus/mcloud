package ru.mentee.power.mcloud_13;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConfig {
  private Connection connection;

  public void connect(String host, String port, String dbName, String user, String password)
      throws SQLException {
    close();
    String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
    Properties props = new Properties();
    props.setProperty("user", user);
    props.setProperty("password", password);
    connection = DriverManager.getConnection(url, props);
    createTables();
  }

  public void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException ignored) {
        // best-effort
      }
      connection = null;
    }
  }

  public void createTables() throws SQLException {
    if (connection == null) {
      return;
    }
    try (var st = connection.createStatement()) {
      st.execute(
          "CREATE TABLE IF NOT EXISTS calculations ("
              + "id SERIAL PRIMARY KEY,"
              + "expression VARCHAR(255),"
              + "result DOUBLE PRECISION,"
              + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
              + ")");
    }
  }

  public void saveCalculation(String expression, double result) {
    if (connection == null) {
      return;
    }
    String sql = "INSERT INTO calculations (expression, result) VALUES (?, ?)";
    try (PreparedStatement ps = connection.prepareStatement(sql)) {
      ps.setString(1, expression);
      ps.setDouble(2, result);
      ps.executeUpdate();
    } catch (SQLException e) {
      System.err.println("Не удалось сохранить расчёт: " + e.getMessage());
    }
  }

  public String getConnectionInfo(String host, String port, String dbName, String user) {
    return String.format(
        "jdbc:postgresql://%s:%s/%s user=%s (пароль скрыт)", host, port, dbName, user);
  }

  boolean isConnected() {
    try {
      return connection != null && !connection.isClosed();
    } catch (SQLException e) {
      return false;
    }
  }
}
