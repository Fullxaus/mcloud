package ru.mentee.power.mcloud_10;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ProcessingService {
  public static void main(String[] args) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    HttpClient client = HttpClient.newHttpClient();

    server.createContext(
        "/process",
        exchange -> {
          try {
            HttpRequest request =
                HttpRequest.newBuilder().uri(URI.create("http://storage:8080/data")).GET().build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            byte[] body = ("Processed: [" + resp.body().toUpperCase() + "]").getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } catch (Exception e) {
            byte[] body = ("Processing error: " + e.getMessage()).getBytes();
            exchange.sendResponseHeaders(502, body.length);
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });

    server.createContext(
        "/health",
        exchange -> {
          byte[] body = "{\"status\":\"UP\",\"service\":\"processing\"}".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.start();
    System.out.println("Processing service started on port 8080");
  }
}
