package ru.mentee.power.vault;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.vault.core.lease.event.SecretLeaseRotatedEvent;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

@DisplayName("Тестирование интеграции с Vault")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = VaultIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VaultIntegrationTest {

  @Container
  static VaultContainer vaultContainer =
      new VaultContainer<>(DockerImageName.parse("hashicorp/vault:1.13.3"))
          .withVaultToken("test-token")
          .withInitCommand(
              "kv put secret/mcloud/database username=testuser password=testpass-secret");

  @Autowired private VaultConfig vaultConfig;
  @Autowired private ApplicationEventPublisher publisher;

  @DynamicPropertySource
  static void vaultProperties(DynamicPropertyRegistry registry) {
    registry.add("vault.uri", () -> vaultContainer.getHttpHostAddress());
    registry.add("vault.token", () -> "test-token");
    registry.add("vault.kubernetes.role", () -> "");
  }

  @Test
  @DisplayName("Должен получить секреты из Vault когда они существуют")
  void shouldRetrieveSecretsFromVault_whenSecretsExist() {
    DatabaseCredentials secrets = vaultConfig.getDynamicCredentials();

    assertThat(secrets).isNotNull();
    assertThat(secrets.username()).isEqualTo("testuser");
    assertThat(secrets.password()).isNotEmpty();
    assertThat(secrets.password()).contains("secret");
  }

  @Test
  @DisplayName("Должен обработать сигнал ротации lease через Spring событие")
  void shouldInvokeRotationHandler_whenRotatedEventPublished() {
    vaultConfig.resetRotationCounterForTests();

    publisher.publishEvent(Mockito.mock(SecretLeaseRotatedEvent.class));

    assertThat(vaultConfig.rotationEventsCountForTests()).isEqualTo(1);
  }
}
