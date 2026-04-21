package ru.mentee.power.mcloud_12;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;

public class CalculatorV1 {
  public static void main(String[] args) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");

    server.createContext(
        "/calculate",
        exchange -> {
          String query = exchange.getRequestURI().getQuery();
          int a = 10, b = 5;
          if (query != null) {
            for (String param : query.split("&")) {
              String[] kv = param.split("=");
              if (kv.length == 2) {
                if ("a".equals(kv[0])) a = Integer.parseInt(kv[1]);
                if ("b".equals(kv[0])) b = Integer.parseInt(kv[1]);
              }
            }
          }
          String json =
              String.format(
                  "{\"result\": %d, \"version\": \"v1\", \"hostname\": \"%s\"}", a + b, hostname);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          byte[] body = json.getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "{\"status\": \"UP\", \"version\": \"v1\"}".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.start();
    System.out.println("Calculator V1 запущен на порту 8080");
  }
}
