package ru.mentee.power.vault;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.KubernetesAuthentication;
import org.springframework.vault.authentication.KubernetesAuthenticationOptions;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.springframework.vault.support.VaultResponse;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableVault
public class VaultConfig {

  @Value("${vault.secret.database-credentials-path:secret/data/mcloud/database}")
  private String databaseCredentialPath;

  private final AtomicInteger leaseRotationSignals = new AtomicInteger();

  @Autowired @Lazy private VaultTemplate vaultTemplate;

  /**
   * KV v2 путь вида {@code secret/data/...}; для полноценных «динамических» пользователей Database
   * Secrets Engine нужен Lease API ({@code LeaseAwareVaultOperationSupport}), что выходит за рамки
   * заготовки {@code VaultTemplate.opsForDatabase()}.
   */
  @SuppressWarnings("unchecked")
  public DatabaseCredentials getDynamicCredentials() {
    VaultResponse response = vaultTemplate.read(databaseCredentialPath);
    if (response == null || response.getData() == null) {
      throw new IllegalStateException("Пустой ответ Vault по пути " + databaseCredentialPath);
    }
    Map<String, Object> root = response.getData();
    Map<String, Object> inner =
        root.get("data") instanceof Map<?, ?> ? (Map<String, Object>) root.get("data") : root;
    String user = string(inner, "username");
    String pass = string(inner, "password");
    return new DatabaseCredentials(user, pass, Optional.empty());
  }

  @EventListener
  public void handleLeaseRotation(SecretLeaseRotatedEvent event) {
    leaseRotationSignals.incrementAndGet();
    // В проде: закрыть старые соединения / пересоздать пул Hikari с новой парой user/pass.
  }

  int rotationEventsCountForTests() {
    return leaseRotationSignals.get();
  }

  void resetRotationCounterForTests() {
    leaseRotationSignals.set(0);
  }

  private static String string(Map<String, Object> map, String key) {
    Object o = map.get(key);
    return o != null ? o.toString() : "";
  }

  @Configuration(proxyBeanMethods = false)
  static class VaultInfrastructure {

    @Bean
    VaultEndpoint vaultEndpoint(@Value("${vault.uri:http://127.0.0.1:8200}") String uri) {
      return VaultEndpoint.from(URI.create(uri));
    }

    /** Token для dev/test; в кластере задайте {@code vault.kubernetes.role} для K8s auth. */
    @Bean
    ClientAuthentication vaultClientAuthentication(
        @Value("${vault.token:}") String token,
        @Value("${vault.kubernetes.role:}") String k8sRole,
        @Value("${vault.kubernetes.jwt-path:/var/run/secrets/kubernetes.io/serviceaccount/token}")
            String jwtPath) {
      if (notBlank(k8sRole)) {
        KubernetesAuthenticationOptions options =
            KubernetesAuthenticationOptions.builder()
                .role(k8sRole.trim())
                .path("kubernetes")
                .jwtSupplier(
                    () -> {
                      try {
                        return Files.readString(Paths.get(jwtPath), StandardCharsets.UTF_8).trim();
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                    })
                .build();
        return new KubernetesAuthentication(options, new RestTemplate());
      }
      return new TokenAuthentication(token);
    }

    @Bean
    VaultTemplate vaultTemplate(VaultEndpoint endpoint, ClientAuthentication authentication) {
      return new VaultTemplate(endpoint, authentication);
    }

    private static boolean notBlank(String s) {
      return s != null && !s.trim().isEmpty();
    }
  }
}
