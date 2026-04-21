package ru.mentee.power.mcloud_12;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class CalculatorV2 {
  private static final AtomicLong requestCount = new AtomicLong(0);
  private static final Instant startTime = Instant.now();

  public static void main(String[] args) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");

    server.createContext(
        "/calculate",
        exchange -> {
          requestCount.incrementAndGet();
          String query = exchange.getRequestURI().getQuery();
          int a = 10, b = 5;
          String op = "add";
          if (query != null) {
            for (String param : query.split("&")) {
              String[] kv = param.split("=");
              if (kv.length == 2) {
                if ("a".equals(kv[0])) a = Integer.parseInt(kv[1]);
                if ("b".equals(kv[0])) b = Integer.parseInt(kv[1]);
                if ("op".equals(kv[0])) op = kv[1];
              }
            }
          }
          int result =
              switch (op) {
                case "sub" -> a - b;
                case "mul" -> a * b;
                case "div" -> b != 0 ? a / b : 0;
                default -> a + b;
              };
          String json =
              String.format(
                  "{\"result\": %d, \"operation\": \"%s\", \"version\": \"v2\","
                      + " \"hostname\": \"%s\", \"enhanced\": true}",
                  result, op, hostname);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          byte[] body = json.getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "{\"status\": \"UP\", \"version\": \"v2\"}".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.createContext(
        "/metrics",
        exchange -> {
          long uptime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
          String json =
              String.format(
                  "{\"requests\": %d, \"uptime_seconds\": %d, \"version\": \"v2\","
                      + " \"hostname\": \"%s\"}",
                  requestCount.get(), uptime, hostname);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          byte[] body = json.getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.start();
    System.out.println("Calculator V2 с улучшениями запущен на порту 8080");
  }
}
