package dev.felippevaz.client;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Requisição montada pelo {@link RestClient}, já com o corpo serializado. */
public class RestRequest {

    private final String method;
    private final String path;
    private final String body;
    private final Map<String, String> headers = new LinkedHashMap<>();

    public RestRequest(String method, String path, String body) {
        this.method = method.toUpperCase();
        this.path = path.startsWith("/") ? path : "/" + path;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    /** Caminho relativo à URL base, incluindo a query string, se houver. */
    public String getPath() {
        return path;
    }

    /** Corpo já serializado, ou null se a requisição não tiver corpo. */
    public String getBody() {
        return body;
    }

    /** Bytes exatos que serão enviados (UTF-8). Vazio se não houver corpo. */
    public byte[] getBodyBytes() {
        return body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    public RestRequest header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}
