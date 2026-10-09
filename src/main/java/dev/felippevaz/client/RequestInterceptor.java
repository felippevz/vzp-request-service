package dev.felippevaz.client;

/**
 * Executado pelo {@link RestClient} logo antes de enviar cada requisição, com o corpo
 * já serializado. Use para adicionar headers calculados (ex.: assinatura HMAC).
 */
@FunctionalInterface
public interface RequestInterceptor {

    void intercept(RestRequest request);
}
