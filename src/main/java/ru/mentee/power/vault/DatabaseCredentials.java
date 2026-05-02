package ru.mentee.power.vault;

import java.util.Optional;

/** Динамические (или статические KV) учётные данные базы из Vault. */
public record DatabaseCredentials(String username, String password, Optional<String> leaseId) {}
