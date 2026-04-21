package ru.mentee.power.mcloud_12;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SimpleWebServer {

  public static void main(String[] args) throws IOException {
    new SimpleWebServer().start();
  }

  public void start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    String hostname = System.getenv().getOrDefault("HOSTNAME", "unknown");

    server.createContext(
        "/",
        exchange -> {
          String html =
              "<html><body>"
                  + "<h1>Simple Web Server</h1>"
                  + "<p>Hostname: "
                  + hostname
                  + "</p>"
                  + "<p><a href=\"/call-calculator\">Call Calculator</a></p>"
                  + "</body></html>";
          exchange.getResponseHeaders().set("Content-Type", "text/html");
          byte[] body = html.getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.createContext(
        "/call-calculator",
        exchange -> {
          String result = callCalculatorService();
          String html =
              "<html><body>"
                  + "<h1>Calculator Response</h1>"
                  + "<pre>"
                  + result
                  + "</pre>"
                  + "<p><a href=\"/\">Back</a></p>"
                  + "</body></html>";
          exchange.getResponseHeaders().set("Content-Type", "text/html");
          byte[] body = html.getBytes();
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    server.start();
    System.out.println("Web Server запущен на порту 8080");
  }

  private String callCalculatorService() {
    try {
      HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create("http://calculator-service:80/calculate"))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.body();
    } catch (Exception e) {
      return "Error calling calculator: " + e.getMessage();
    }
  }
}
