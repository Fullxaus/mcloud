package ru.mentee.power.vault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ru.mentee.power.vault")
public class VaultIntegrationTestApplication {

  public static void main(String[] args) {
    SpringApplication.run(VaultIntegrationTestApplication.class, args);
  }
}
