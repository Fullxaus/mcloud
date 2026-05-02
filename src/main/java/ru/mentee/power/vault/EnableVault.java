package ru.mentee.power.vault;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Включает поддержку Vault: {@link VaultLeaseAutoConfiguration} и регистрирует {@link
 * org.springframework.vault.core.lease.SecretLeaseContainer} для lease/ротаций.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({VaultLeaseAutoConfiguration.class})
public @interface EnableVault {}
