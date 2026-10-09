package dev.felippevaz.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma rota (ou um controller inteiro) como pública: ela não passa pelo
 * {@link dev.felippevaz.security.Authenticator} configurado no servidor.
 * A allowlist de IP continua valendo normalmente.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Public {
}
