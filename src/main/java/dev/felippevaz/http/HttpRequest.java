package dev.felippevaz.http;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class HttpRequest {

    private final String method;
    private final String path;
    private final String query;
    private final Map<String, String> headers;
    private final byte[] rawBody;
    private final String body;
    private final InetAddress remoteAddress;
    private final HttpExchange exchange;

    private Map<String, String> queryParameters;

    // Marcado pelo HttpResponse no primeiro envio. Evita que uma segunda resposta
    // (ex.: o ok() automático do RequestHandler depois do controller já ter
    // respondido) tente reenviar headers na mesma exchange.
    private volatile boolean responded = false;

    public HttpRequest(String method, String path, String query, Map<String, String> headers,
                       byte[] rawBody, InetAddress remoteAddress, HttpExchange exchange) {

        this.method = method;
        this.path = path;
        this.query = query;
        this.rawBody = rawBody != null ? rawBody : new byte[0];
        this.body = new String(this.rawBody, StandardCharsets.UTF_8);
        this.remoteAddress = remoteAddress;
        this.exchange = exchange;

        // O JDK normaliza os nomes de header ("X-Api-Key" vira "X-api-key"), então
        // a busca precisa ignorar maiúsculas/minúsculas.
        Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null)
            caseInsensitive.putAll(headers);
        this.headers = Collections.unmodifiableMap(caseInsensitive);
    }

    public HttpRequest(String method, String path, Map<String, String> headers, String body, HttpExchange exchange) {
        this(method, path, null, headers,
                body != null ? body.getBytes(StandardCharsets.UTF_8) : null,
                exchange != null && exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress() : null,
                exchange);
    }

    /**
     * Desserializa o corpo JSON. JSON malformado vira {@link Errors#BAD_REQUEST} (400),
     * e não um erro interno.
     */
    public <T> T getObjectBody(Class<T> objectClass) {
        try {
            return HttpUtils.GSON.fromJson(this.body, objectClass);
        } catch (JsonParseException exception) {
            throw new ApplicationException(Errors.BAD_REQUEST, exception);
        }
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    /** Query string crua (sem o "?"), ou null se não houver. */
    public String getQuery() {
        return query;
    }

    /** Primeiro valor de um parâmetro da query string, já decodificado, ou null. */
    public String getQueryParameter(String name) {

        if (queryParameters == null)
            queryParameters = parseQuery(query);

        return queryParameters.get(name);
    }

    /** Headers da requisição. A busca por nome ignora maiúsculas/minúsculas. */
    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public String getBody() {
        return body;
    }

    /** Bytes exatos do corpo, como recebidos (útil para validar assinaturas). */
    public byte[] getRawBody() {
        return rawBody.clone();
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public HttpExchange getExchange() {
        return this.exchange;
    }

    public boolean isResponded() {
        return responded;
    }

    void markResponded() {
        this.responded = true;
    }

    private static Map<String, String> parseQuery(String query) {

        Map<String, String> parameters = new HashMap<>();

        if (query == null || query.isEmpty())
            return parameters;

        for (String pair : query.split("&")) {

            if (pair.isEmpty())
                continue;

            int separator = pair.indexOf('=');
            String key = decode(separator >= 0 ? pair.substring(0, separator) : pair);
            String value = separator >= 0 ? decode(pair.substring(separator + 1)) : "";

            if (!parameters.containsKey(key))
                parameters.put(key, value);
        }

        return parameters;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException exception) {
            return value;
        }
    }
}
