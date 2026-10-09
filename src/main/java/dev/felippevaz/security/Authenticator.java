package dev.felippevaz.security;

import dev.felippevaz.http.HttpRequest;

/**
 * Hook executado antes de qualquer controller (exceto rotas {@link dev.felippevaz.annotations.Public}).
 * <p>
 * Para recusar a requisição, lance uma {@link dev.felippevaz.exceptions.ApplicationException}
 * (normalmente com {@link dev.felippevaz.exceptions.Errors#UNAUTHORIZED}). Se o método
 * retornar normalmente, a requisição segue para o controller.
 */
@FunctionalInterface
public interface Authenticator {

    void authenticate(HttpRequest request);
}
