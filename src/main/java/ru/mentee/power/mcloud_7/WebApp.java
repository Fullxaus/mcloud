package ru.mentee.power.mcloud_7;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class WebApp {
  private static final AtomicInteger counter = new AtomicInteger(0);

  public static void main(String[] args) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

    server.createContext(
        "/",
        exchange -> {
          String response = "Welcome! Visitor #" + counter.incrementAndGet();
          exchange.sendResponseHeaders(200, response.length());
          exchange.getResponseBody().write(response.getBytes());
          exchange.close();
        });

    server.createContext(
        "/health",
        exchange -> {
          String response = "{\"status\":\"UP\"}";
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length());
          exchange.getResponseBody().write(response.getBytes());
          exchange.close();
        });

    server.start();
    System.out.println("Server running on http://localhost:8080");
  }
}
