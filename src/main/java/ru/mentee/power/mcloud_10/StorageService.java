package ru.mentee.power.mcloud_10;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;

public class StorageService {
  private static final String REDIS_HOST = "redis";
  private static final int REDIS_PORT = 6379;

  public static void main(String[] args) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

    Thread.sleep(2000);
    redisSet("app:data", "Initial data loaded at " + LocalDateTime.now());
    System.out.println("Redis initialized successfully");

    server.createContext(
        "/data",
        exchange -> {
          try {
            String data = redisGet("app:data");
            if (data == null) {
              data = "Sample data from storage at " + LocalDateTime.now();
              redisSet("app:data", data);
            }
            byte[] body = data.getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } catch (Exception e) {
            byte[] body = ("Storage error: " + e.getMessage()).getBytes();
            exchange.sendResponseHeaders(500, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });

    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "{\"status\":\"UP\",\"service\":\"storage\"}".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.start();
    System.out.println("Storage service started on port 8080");
  }

  private static String redisGet(String key) throws Exception {
    try (Socket socket = new Socket(REDIS_HOST, REDIS_PORT)) {
      OutputStream out = socket.getOutputStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String cmd = "*2\r\n$3\r\nGET\r\n$" + key.getBytes().length + "\r\n" + key + "\r\n";
      out.write(cmd.getBytes());
      out.flush();
      String line = in.readLine();
      if (line.startsWith("$-1")) return null;
      if (line.startsWith("$")) return in.readLine();
      return line;
    }
  }

  private static void redisSet(String key, String value) throws Exception {
    try (Socket socket = new Socket(REDIS_HOST, REDIS_PORT)) {
      OutputStream out = socket.getOutputStream();
      BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      String cmd =
          "*3\r\n$3\r\nSET\r\n$"
              + key.getBytes().length
              + "\r\n"
              + key
              + "\r\n$"
              + value.getBytes().length
              + "\r\n"
              + value
              + "\r\n";
      out.write(cmd.getBytes());
      out.flush();
      in.readLine();
    }
  }
}
