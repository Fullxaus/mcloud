package ru.mentee.power.vault;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;

@Configuration(proxyBeanMethods = false)
class VaultLeaseAutoConfiguration {

  @Bean(name = "vaultLeaseTaskScheduler")
  ThreadPoolTaskScheduler vaultLeaseTaskScheduler() {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(2);
    scheduler.setThreadNamePrefix("vault-lease-");
    scheduler.initialize();
    return scheduler;
  }

  @Bean
  SecretLeaseContainer secretLeaseContainer(
      VaultTemplate vaultTemplate, ThreadPoolTaskScheduler vaultLeaseTaskScheduler) {
    return new SecretLeaseContainer(vaultTemplate, vaultLeaseTaskScheduler);
  }
}
