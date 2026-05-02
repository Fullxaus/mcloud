package ru.mentee.power.mcloud_13;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ConfigurableCalculator {

  static final Path CONFIG_FILE = Path.of("/app/config/application.properties");
  static final Path SECONDARY_CONFIG_FILE = Path.of("/app/config/database.properties");
  static final Path SECRET_DIR = Path.of("/app/secrets");

  private Properties config = new Properties();
  private ConnectionHolder dbHolder = new ConnectionHolder();
  private String databasePassword;
  private String databaseUser;
  private String apiKey;
  private ExternalApiClient externalApiClient;

  public static void main(String[] args) throws IOException {
    new ConfigurableCalculator().start();
  }

  public void start() throws IOException {
    loadConfiguration();
    initDatabase();

    int port = Integer.parseInt(getConfig("SERVER_PORT", "8080"));
    HttpServer server = HttpServer.create(new java.net.InetSocketAddress(port), 0);

    server.createContext(
        "/",
        exchange -> {
          try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              send(exchange, 405, "{\"error\":\"method not allowed\"}", "application/json");
              return;
            }
            String body =
                "{\"service\":\"ConfigurableCalculator\",\"index\": "
                    + sanitizeConfigJson(safePublicConfigSummary())
                    + "}";
            send(exchange, 200, body, "application/json");
          } finally {
            exchange.close();
          }
        });

    server.createContext(
        "/calculate",
        exchange -> {
          try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              send(exchange, 405, "{\"error\":\"method not allowed\"}", "application/json");
              return;
            }
            var parsed = parseQuery(exchange.getRequestURI().getQuery());
            int a = Integer.parseInt(parsed.getOrDefault("a", "10"));
            int b = Integer.parseInt(parsed.getOrDefault("b", "5"));
            String op = parsed.getOrDefault("op", "add");

            double result =
                switch (op) {
                  case "sub" -> a - b;
                  case "mul" -> a * (double) b;
                  case "div" -> (b != 0) ? a / (double) b : Double.NaN;
                  default -> a + b;
                };
            String expression = a + " " + operatorSymbol(op) + " " + b;
            boolean saveToDb =
                Boolean.parseBoolean(getConfig("SAVE_TO_DATABASE", Boolean.TRUE.toString()));
            if (saveToDb && dbHolder.connected()) {
              dbHolder.save(expression, result);
            }
            String hostname = System.getenv().getOrDefault("HOSTNAME", "local");
            String json =
                String.format(
                    "{\"expression\": \"%s\", \"result\": %s, \"saved_to_database\": %s, \"hostname\": \"%s\"}",
                    sanitizeForEmbeddedJson(expression),
                    Double.toString(result),
                    Boolean.toString(saveToDb && dbHolder.connected()),
                    sanitizeForEmbeddedJson(hostname));
            send(exchange, 200, json, "application/json");
          } catch (Exception e) {
            send(
                exchange,
                400,
                "{\"error\": \"" + sanitizeForEmbeddedJson(e.getMessage()) + "\"}",
                "application/json");
          } finally {
            exchange.close();
          }
        });

    server.createContext(
        "/config",
        exchange -> {
          try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              send(exchange, 405, "{\"error\":\"method not allowed\"}", "application/json");
              return;
            }
            String body = "{\"config\": " + sanitizeConfigJson(publicConfigMap()) + "}";
            send(exchange, 200, body, "application/json");
          } finally {
            exchange.close();
          }
        });

    server.createContext(
        "/external",
        exchange -> {
          try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
              send(exchange, 405, "{\"error\":\"method not allowed\"}", "application/json");
              return;
            }
            Map<String, String> q = parseQuery(exchange.getRequestURI().getQuery());
            String endpoint = q.getOrDefault("path", "/get");
            if (externalApiClient == null) {
              send(
                  exchange,
                  503,
                  "{\"error\":\"EXTERNAL_API_BASE не задан или API_KEY недоступен\"}",
                  "application/json");
              return;
            }
            String body = externalApiClient.callExternalApi(endpoint);
            send(exchange, 200, body, "application/json");
          } finally {
            exchange.close();
          }
        });

    server.start();
    System.out.println("Configurable Calculator запущен на порту " + port);
  }

  private static String operatorSymbol(String op) {
    return switch (op) {
      case "sub" -> "-";
      case "mul" -> "*";
      case "div" -> "/";
      default -> "+";
    };
  }

  private void loadConfiguration() {
    Properties merged = defaults();
    loadFileInto(merged, CONFIG_FILE);
    loadFileInto(merged, SECONDARY_CONFIG_FILE);

    overlayFromEnv(
        merged,
        "SERVER_PORT",
        "LOG_LEVEL",
        "DATABASE_HOST",
        "DATABASE_PORT",
        "DATABASE_NAME",
        "CACHE_ENABLED",
        "SAVE_TO_DATABASE",
        "EXTERNAL_API_BASE",
        "EXTERNAL_API_VALIDATE_PATH");

    config = merged;

    databasePassword = readSecretEnvOrFile("DATABASE_PASSWORD", "database-password");
    databaseUser = readSecretEnvOrFile("DATABASE_USER", "database-user");
    apiKey = readSecretEnvOrFile("API_KEY", "api-key");

    databaseUser =
        databaseUser == null
            ? merged.getProperty("DATABASE_USER_FALLBACK", "calculator")
            : databaseUser;
    databasePassword = databasePassword == null ? "" : databasePassword;
    apiKey = apiKey == null ? "" : apiKey;

    ensureJwtLogged();

    String base =
        merged.getProperty("EXTERNAL_API_BASE") == null
            ? ""
            : merged.getProperty("EXTERNAL_API_BASE").trim();
    if (!apiKey.isEmpty() && !base.isEmpty()) {
      externalApiClient = new ExternalApiClient(apiKey, base);
    } else {
      externalApiClient = null;
      System.err.println(
          "Предупреждение: EXTERNAL_API_BASE или API_KEY не заданы — /external недоступен");
    }

    printLoadedNonSensitive();
  }

  private static Properties defaults() {
    Properties p = new Properties();
    p.setProperty("SERVER_PORT", "8080");
    p.setProperty("LOG_LEVEL", "INFO");
    p.setProperty("DATABASE_HOST", "localhost");
    p.setProperty("DATABASE_PORT", "5432");
    p.setProperty("DATABASE_NAME", "calculator");
    p.setProperty("CACHE_ENABLED", "false");
    p.setProperty("SAVE_TO_DATABASE", "true");
    p.setProperty("EXTERNAL_API_BASE", "");
    p.setProperty("EXTERNAL_API_VALIDATE_PATH", "/validate");
    return p;
  }

  private static void loadFileInto(Properties target, Path file) {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try (InputStream in = Files.newInputStream(file)) {
      Properties extra = new Properties();
      extra.load(in);
      target.putAll(extra);
    } catch (IOException e) {
      System.err.println("Не удалось прочитать " + file + ": " + e.getMessage());
    }
  }

  private static void overlayFromEnv(Properties target, String... keys) {
    for (String k : keys) {
      String v = System.getenv(k);
      if (v != null && !v.isEmpty()) {
        target.setProperty(k, v);
      }
    }
  }

  /** Секреты: сначала env (как задаёт Deployment), иначе файлы из монтированного Secret. */
  private static String readSecretEnvOrFile(String envName, String secretFileBaseName) {
    String env = System.getenv(envName);
    if (env != null && !env.isEmpty()) {
      return env;
    }
    Path p = SECRET_DIR.resolve(secretFileBaseName);
    if (!Files.isRegularFile(p)) {
      return null;
    }
    try {
      return Files.readString(p, StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      System.err.println("Не удалось прочитать секрет " + p + ": " + e.getMessage());
      return null;
    }
  }

  private void printLoadedNonSensitive() {
    System.out.println("Конфигурация загружена (без секретов):");
    Properties copy = filterPublic(config);
    copy.stringPropertyNames().stream()
        .sorted()
        .forEach(k -> System.out.println("  " + k + "=" + copy.getProperty(k)));
    System.out.println("  DATABASE_USER=" + maskUser(databaseUser));
    System.out.println(
        "  DATABASE_PASSWORD=" + masked(databasePassword != null && !databasePassword.isEmpty()));
    System.out.println("  API_KEY=" + masked(apiKey != null && !apiKey.isEmpty()));
  }

  private static Properties filterPublic(Properties source) {
    Properties p = new Properties();
    source.stringPropertyNames().stream()
        .filter(k -> !isSensitiveKey(k))
        .forEach(k -> p.setProperty(k, source.getProperty(k)));
    return p;
  }

  /** JWT из Secret нужен только для демонстрации монтирования; значение не выводится. */
  private void ensureJwtLogged() {
    String jwt = readSecretEnvOrFile("JWT_SECRET", "jwt-secret");
    if (jwt != null && !jwt.isEmpty()) {
      System.out.println("JWT_SECRET загружен (длина символов: " + jwt.length() + ")");
    }
  }

  private static boolean isSensitiveKey(String key) {
    String u = key.toUpperCase();
    return u.contains("PASSWORD")
        || u.contains("SECRET")
        || "API_KEY".equals(u)
        || u.contains("TOKEN");
  }

  private static String sanitizeConfigJson(Map<String, String> map) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    List<String> keys = new ArrayList<>(map.keySet());
    keys.sort(String::compareTo);
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      String k = keys.get(i);
      sb.append("\"").append(escape(k)).append("\":\"").append(escape(map.get(k))).append("\"");
    }
    sb.append('}');
    return sb.toString();
  }

  private Map<String, String> safePublicConfigSummary() {
    Map<String, String> map = publicConfigMap();
    map.put("database_user_masked", maskUser(databaseUser));
    map.put(
        "database_connection",
        dbHolder.connected()
            ? dbHolder.publicInfo(config)
            : "не установлено (проверьте хост, пароль, PostgreSQL)");
    map.put(
        "external_api", externalApiClient == null ? "не настроен" : configuredExternalSummary());
    return map;
  }

  private String configuredExternalSummary() {
    boolean ok =
        externalApiClient.validateApiKey(getConfig("EXTERNAL_API_VALIDATE_PATH", "/validate"));
    String base = getConfig("EXTERNAL_API_BASE", "");
    return base
        + " (validate="
        + ok
        + "; путь="
        + getConfig("EXTERNAL_API_VALIDATE_PATH", "/validate")
        + ")";
  }

  private Map<String, String> publicConfigMap() {
    Map<String, String> out = new LinkedHashMap<>();
    for (String name : config.stringPropertyNames().stream().sorted().toList()) {
      if (isSensitiveKey(name)) {
        continue;
      }
      out.put(name, config.getProperty(name));
    }
    out.putIfAbsent(
        "note",
        "Секреты (пароли, jwt, api-key) здесь не отображаются; только ConfigMap/application.properties.");
    return out;
  }

  private void initDatabase() {
    String host = getConfig("DATABASE_HOST", "localhost");
    String port = getConfig("DATABASE_PORT", "5432");
    String name = getConfig("DATABASE_NAME", "calculator");

    if (databasePassword.isEmpty()) {
      System.err.println("DATABASE_PASSWORD отсутствует — база недоступна");
      dbHolder.disconnect();
      return;
    }

    DatabaseConfig dc = new DatabaseConfig();
    try {
      dc.connect(host, port, name, databaseUser, databasePassword);
      dbHolder.set(dc, host, port, name, databaseUser);
      System.out.println(
          "PostgreSQL подключён: " + dc.getConnectionInfo(host, port, name, databaseUser));
    } catch (Exception e) {
      dbHolder.disconnect();
      System.err.println("База данных недоступна: " + e.getMessage());
    }
  }

  String getConfig(String key, String defaultValue) {
    String env = System.getenv(key);
    if (env != null && !env.isEmpty()) {
      return env;
    }
    return config.getProperty(key, defaultValue);
  }

  private static void send(
      com.sun.net.httpserver.HttpExchange exchange, int code, String body, String contentType)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
    exchange.sendResponseHeaders(code, bytes.length);
    exchange.getResponseBody().write(bytes);
  }

  private static Map<String, String> parseQuery(String query) {
    Map<String, String> map = new LinkedHashMap<>();
    if (query == null || query.isEmpty()) {
      return map;
    }
    for (String part : query.split("&")) {
      String[] kv = part.split("=", 2);
      if (kv.length == 2) {
        map.put(kv[0], kv[1]);
      }
    }
    return map;
  }

  private static String escape(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String sanitizeForEmbeddedJson(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String maskUser(String user) {
    if (user == null || user.isEmpty()) {
      return "(не задан)";
    }
    if (user.length() <= 2) {
      return user.charAt(0) + "*";
    }
    return user.substring(0, 2) + "***";
  }

  private static String masked(boolean present) {
    return present ? "****" : "(не задан)";
  }

  /** Обёртка, чтобы основной класс не таскал JDBC Connection напрямую. */
  private static final class ConnectionHolder {
    private DatabaseConfig delegate;
    private String host;
    private String port;
    private String dbName;
    private String user;

    void set(DatabaseConfig dc, String host, String port, String dbName, String user) {
      this.delegate = dc;
      this.host = host;
      this.port = port;
      this.dbName = dbName;
      this.user = user;
    }

    void disconnect() {
      if (delegate != null) {
        delegate.close();
        delegate = null;
      }
    }

    boolean connected() {
      return delegate != null && delegate.isConnected();
    }

    void save(String expr, double result) {
      if (delegate != null) {
        delegate.saveCalculation(expr, result);
      }
    }

    String publicInfo(Properties fallback) {
      if (delegate == null) {
        return "";
      }
      return delegate.getConnectionInfo(
          host == null ? fallback.getProperty("DATABASE_HOST") : host,
          port == null ? fallback.getProperty("DATABASE_PORT") : port,
          dbName == null ? fallback.getProperty("DATABASE_NAME") : dbName,
          maskUserPublic(user));
    }

    private static String maskUserPublic(String user) {
      return user == null ? "" : user;
    }
  }
}
