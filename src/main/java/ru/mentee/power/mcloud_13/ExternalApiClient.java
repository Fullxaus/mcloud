package ru.mentee.power.mcloud_13;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ExternalApiClient {
  private final HttpClient client =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  private final String apiKey;
  private final String apiUrl;

  public ExternalApiClient(String apiKey, String apiUrl) {
    this.apiKey = apiKey == null ? "" : apiKey;
    this.apiUrl =
        apiUrl == null
            ? ""
            : apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
  }

  /** Вызывает внешнее API и возвращает тело ответа (или описание ошибки). */
  public String callExternalApi(String endpoint) {
    String path = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
    URI uri = URI.create(apiUrl + path);
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + apiKey)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return "{\"status\": "
          + response.statusCode()
          + ", \"body\": "
          + escapeJson(response.body())
          + "}";
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return "{\"error\": " + escapeJson(e.getMessage()) + "}";
    }
  }

  /**
   * Вызывает внешний endpoint для проверки ключа (по умолчанию `/validate`; для httpbin —
   * `/bearer`).
   */
  public boolean validateApiKey(String validatePathSuffix) {
    String path =
        validatePathSuffix == null || validatePathSuffix.isEmpty()
            ? "/validate"
            : validatePathSuffix.startsWith("/") ? validatePathSuffix : "/" + validatePathSuffix;
    URI uri = URI.create(apiUrl + path);
    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .timeout(Duration.ofSeconds(10))
              .header("Authorization", "Bearer " + apiKey)
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 300;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String escapeJson(String s) {
    if (s == null) {
      return "\"\"";
    }
    String escaped =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    return "\"" + escaped + "\"";
  }
}
