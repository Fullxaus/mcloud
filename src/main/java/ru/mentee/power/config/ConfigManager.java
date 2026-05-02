package ru.mentee.power.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Конфигурация из типичного монтирования ConfigMap под {@code /config} (+ env, значения по
 * умолчанию). Hot-reload через {@link java.nio.file.WatchService}.
 */
public class ConfigManager {

  /** Путь из подсказки задания. */
  public static final Path CONFIG_PROPERTIES = Path.of("/config/application.properties");

  public static final Path SECRETS_DIR = Path.of("/secrets");

  private static final ExecutorService WATCH_POOL =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread t = new Thread(runnable, "config-watch");
            t.setDaemon(true);
            return t;
          });

  private volatile Properties configuration = defaults();
  private volatile Credentials secrets = Credentials.empty();

  private final java.util.concurrent.CopyOnWriteArrayList<Consumer<Properties>> configListeners =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  private final AtomicBoolean watchRunning = new AtomicBoolean(false);
  private volatile Future<?> watchTask;

  public Properties loadConfiguration() {
    Properties merged = defaults();
    if (Files.isRegularFile(CONFIG_PROPERTIES)) {
      try (InputStream in = Files.newInputStream(CONFIG_PROPERTIES)) {
        Properties fromFile = new Properties();
        fromFile.load(in);
        merged.putAll(fromFile);
      } catch (IOException e) {
        throw new UncheckedIOException("Не удалось прочитать " + CONFIG_PROPERTIES, e);
      }
    }
    overlayKnownEnvKeys(merged);
    configuration = merged;
    return copyProps(merged);
  }

  /** Секреты: переменные окружения и/или смонтированные файлы под {@link #SECRETS_DIR}. */
  public Credentials loadSecrets() {
    Credentials c =
        new Credentials(
            firstNonBlank(
                env("DATABASE_USER"),
                env("DB_USERNAME"),
                fileUtf8(SECRETS_DIR.resolve("database-user")),
                ""),
            firstNonBlank(
                env("DATABASE_PASSWORD"),
                env("DB_PASSWORD"),
                fileUtf8(SECRETS_DIR.resolve("database-password")),
                ""),
            Optional.ofNullable(
                firstNullable(env("API_KEY"), fileUtf8(SECRETS_DIR.resolve("api-key")))));
    secrets = c;
    return c;
  }

  /**
   * Hot-reload конфигурации при изменении {@code application.properties} в каталоге {@code
   * /config}. Альтернатива в экосистеме Spring — Spring Cloud Kubernetes {@code reload}.
   */
  public synchronized void watchConfigChanges() {
    watchConfigChanges(ignored -> {});
  }

  public synchronized void watchConfigChanges(Consumer<Properties> onReload) {
    Objects.requireNonNull(onReload);
    Path watchDir =
        CONFIG_PROPERTIES.getParent() != null ? CONFIG_PROPERTIES.getParent() : CONFIG_PROPERTIES;
    if (!Files.isDirectory(watchDir)) {
      throw new IllegalStateException(
          "Каталог недоступен для watch: "
              + watchDir
              + " (смонтируйте ConfigMap, например, в /config)");
    }
    if (watchRunning.compareAndSet(false, true)) {
      watchTask = WATCH_POOL.submit(() -> runWatchLoop(watchDir, onReload));
    }
  }

  /** Для тестов: наблюдает указанный каталог (необязательно {@code /config}). */
  public synchronized void watchConfigDirectory(Path dir, Consumer<Properties> listener) {
    Objects.requireNonNull(dir);
    Objects.requireNonNull(listener);
    if (!Files.isDirectory(dir)) {
      throw new IllegalArgumentException("Не каталог: " + dir);
    }
    if (watchRunning.compareAndSet(false, true)) {
      watchTask =
          WATCH_POOL.submit(
              () -> watchDirectoryLoop(dir, CONFIG_PROPERTIES.getFileName(), listener));
    }
  }

  public void stopWatch() {
    watchRunning.set(false);
    Future<?> ft = watchTask;
    if (ft != null) {
      ft.cancel(true);
      watchTask = null;
    }
  }

  public Properties currentConfigurationSnapshot() {
    return copyProps(configuration);
  }

  public Credentials currentSecretsSnapshot() {
    return secrets;
  }

  public void addConfigReloadListener(Consumer<Properties> listener) {
    configListeners.add(listener);
  }

  private void runWatchLoop(Path watchDir, Consumer<Properties> listener) {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      registerWatch(watchDir, watchService);
      pollLoop(watchDir, listener, CONFIG_PROPERTIES.getFileName(), watchService);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      watchRunning.set(false);
    }
  }

  private void watchDirectoryLoop(Path dir, Path fileNameFilter, Consumer<Properties> listener) {
    try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
      registerWatch(dir, watchService);
      pollLoop(dir, listener, fileNameFilter, watchService);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      watchRunning.set(false);
    }
  }

  private static void registerWatch(Path watchDir, WatchService watchService) throws IOException {
    watchDir.register(
        watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
  }

  private void pollLoop(
      Path dir, Consumer<Properties> listener, Path fileFilter, WatchService watchService)
      throws InterruptedException {
    while (watchRunning.get()) {
      WatchKey key = watchService.take();
      try {
        for (java.nio.file.WatchEvent<?> evt : key.pollEvents()) {
          Path name = (Path) evt.context();
          if (name != null && fileFilter.equals(name)) {
            Properties p = loadConfiguration();
            listener.accept(copyProps(p));
            for (Consumer<Properties> lr : configListeners) {
              try {
                lr.accept(copyProps(configuration));
              } catch (Exception ex) {
                System.err.println("config listener failed: " + ex.getMessage());
              }
            }
          }
        }
      } finally {
        if (!key.reset()) {
          break;
        }
      }
    }
  }

  private static Properties defaults() {
    Properties p = new Properties();
    p.setProperty("app.name", "mcloud-config");
    p.setProperty("app.env", "default");
    return p;
  }

  private static void overlayKnownEnvKeys(Properties merged) {
    Set<String> keys =
        Set.of(
            "SERVER_PORT",
            "LOG_LEVEL",
            "DATABASE_HOST",
            "DATABASE_PORT",
            "DATABASE_NAME",
            "CACHE_ENABLED");
    for (String k : keys) {
      String v = env(k);
      if (v != null && !v.isBlank()) {
        merged.setProperty(k.toLowerCase().replace("_", "."), v);
        merged.setProperty(k, v);
      }
    }
  }

  private static String env(String key) {
    return System.getenv(key);
  }

  private static String fileUtf8(Path p) {
    if (!Files.isRegularFile(p)) {
      return null;
    }
    try {
      return Files.readString(p, StandardCharsets.UTF_8).trim();
    } catch (IOException e) {
      return null;
    }
  }

  private static String firstNonBlank(String a, String b, String fileVal, String dflt) {
    if (notBlank(a)) {
      return a;
    }
    if (notBlank(b)) {
      return b;
    }
    if (notBlank(fileVal)) {
      return fileVal;
    }
    return dflt == null ? "" : dflt;
  }

  private static String firstNullable(String a, String b) {
    if (notBlank(a)) {
      return a;
    }
    return notBlank(b) ? b : null;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static Properties copyProps(Properties src) {
    Properties out = new Properties();
    src.stringPropertyNames().forEach(k -> out.setProperty(k, src.getProperty(k)));
    return out;
  }
}
