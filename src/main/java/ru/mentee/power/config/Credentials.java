package ru.mentee.power.config;

import java.util.Optional;

/** Учётные данные из Secret (переменные окружения и/или смонтированные файлы). */
public record Credentials(String username, String password, Optional<String> apiKey) {
  public static Credentials empty() {
    return new Credentials("", "", Optional.empty());
  }
}
