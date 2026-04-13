package ru.mentee.power.mcloud_9;

import java.sql.*;
import java.util.Scanner;

public class TodoApp {
  private static final String DEFAULT_URL =
      "jdbc:postgresql://localhost:5432/tododb?user=todouser&password=todopass";
  private static final String DB_URL =
      System.getenv("DATABASE_URL") != null ? System.getenv("DATABASE_URL") : DEFAULT_URL;

  public static void main(String[] args) throws Exception {
    if (System.getenv("DATABASE_URL") != null) {
      Thread.sleep(5000);
    }

    try (Connection conn = DriverManager.getConnection(DB_URL)) {
      initDatabase(conn);

      Scanner scanner = new Scanner(System.in);
      while (true) {
        System.out.println("\n=== TODO List ===");
        System.out.println("1. Показать задачи");
        System.out.println("2. Добавить задачу");
        System.out.println("3. Отметить выполненной");
        System.out.println("4. Удалить задачу");
        System.out.println("0. Выход");

        int choice = scanner.nextInt();
        scanner.nextLine();

        switch (choice) {
          case 1 -> showTodos(conn);
          case 2 -> {
            System.out.print("Введите задачу: ");
            String task = scanner.nextLine();
            addTodo(conn, task);
          }
          case 3 -> {
            System.out.print("ID задачи: ");
            int id = scanner.nextInt();
            completeTodo(conn, id);
          }
          case 4 -> {
            System.out.print("ID задачи: ");
            int id = scanner.nextInt();
            deleteTodo(conn, id);
          }
          case 0 -> {
            System.out.println("До свидания!");
            return;
          }
        }
      }
    }
  }

  private static void initDatabase(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();
    stmt.execute(
        "CREATE TABLE IF NOT EXISTS todos ("
            + "id SERIAL PRIMARY KEY, "
            + "task VARCHAR(255) NOT NULL, "
            + "completed BOOLEAN DEFAULT FALSE, "
            + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
  }

  private static void showTodos(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM todos ORDER BY id");
    System.out.println("\nID | Задача | Статус | Создана");
    System.out.println("---|--------|--------|--------");
    while (rs.next()) {
      System.out.printf(
          "%d | %s | %s | %s%n",
          rs.getInt("id"),
          rs.getString("task"),
          rs.getBoolean("completed") ? "V" : "X",
          rs.getTimestamp("created_at"));
    }
  }

  private static void addTodo(Connection conn, String task) throws SQLException {
    PreparedStatement ps = conn.prepareStatement("INSERT INTO todos (task) VALUES (?)");
    ps.setString(1, task);
    ps.executeUpdate();
    System.out.println("Задача добавлена!");
  }

  private static void completeTodo(Connection conn, int id) throws SQLException {
    PreparedStatement ps =
        conn.prepareStatement("UPDATE todos SET completed = true WHERE id = ?");
    ps.setInt(1, id);
    int rows = ps.executeUpdate();
    if (rows > 0) {
      System.out.println("Задача отмечена как выполненная!");
    } else {
      System.out.println("Задача с ID " + id + " не найдена.");
    }
  }

  private static void deleteTodo(Connection conn, int id) throws SQLException {
    PreparedStatement ps = conn.prepareStatement("DELETE FROM todos WHERE id = ?");
    ps.setInt(1, id);
    int rows = ps.executeUpdate();
    if (rows > 0) {
      System.out.println("Задача удалена!");
    } else {
      System.out.println("Задача с ID " + id + " не найдена.");
    }
  }
}
