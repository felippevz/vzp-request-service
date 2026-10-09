package dev.felippevaz.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Resposta recebida pelo {@link RestClient}. Status de erro (4xx/5xx) não lançam exceção. */
public class RestResponse {

    private final int status;
    private final String body;
    private final Map<String, String> headers;
    private final Gson gson;

    RestResponse(int status, String body, Map<String, String> headers, Gson gson) {
        this.status = status;
        this.body = body != null ? body : "";
        this.gson = gson;

        Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(headers);
        this.headers = Collections.unmodifiableMap(caseInsensitive);
    }

    public int getStatus() {
        return status;
    }

    /** true para status 2xx. */
    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    public String getBody() {
        return body;
    }

    /**
     * Desserializa o corpo JSON.
     *
     * @throws ApplicationException {@link Errors#CLIENT_REQUEST_ERROR} se o corpo não for JSON válido.
     */
    public <T> T as(Class<T> type) {
        try {
            return gson.fromJson(body, type);
        } catch (JsonParseException exception) {
            throw new ApplicationException(Errors.CLIENT_REQUEST_ERROR, exception);
        }
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}
