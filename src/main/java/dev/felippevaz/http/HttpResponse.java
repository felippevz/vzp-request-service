package dev.felippevaz.http;

import com.google.gson.JsonObject;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class HttpResponse {

    private static final Logger LOGGER = Logger.getLogger(HttpResponse.class.getName());

    private int status = 200;
    private JsonObject body = new JsonObject();
    private final Map<String, String> headers = new HashMap<>();
    private boolean sent = false;

    public HttpResponse() {
        this.setHeader("Content-Type", "application/json");
        this.body.addProperty("timestamp", System.currentTimeMillis());
    }

    public HttpResponse setStatus(int status) {
        this.status = status;
        return this;
    }

    public void clearFields() {
        this.body = new JsonObject();
    }

    public HttpResponse setBody(JsonObject jsonObject) {
        this.body = jsonObject;
        return this;
    }

    public HttpResponse addFieldBody(String key, String value) {
        this.body.addProperty(key, value);
        return this;
    }

    public HttpResponse setHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public HttpResponse addObject(String property, Object object) {
        this.body.add(property, HttpUtils.GSON.toJsonTree(object));
        return this;
    }

    public HttpResponse addListObjects(String property, List<?> objects) {
        this.body.add(property, HttpUtils.GSON.toJsonTree(objects));
        return this;
    }

    /**
     * Envia a resposta. Só a primeira resposta de uma requisição é enviada:
     * chamadas seguintes (nesta ou em outra instância de HttpResponse) são ignoradas.
     */
    public void send(HttpRequest request) {

        if (this.sent || request.isResponded())
            return;

        this.sent = true;
        request.markResponded();

        this.headers.forEach((k, v) ->
                request.getExchange().getResponseHeaders().add(k, v)
        );

        if (body == null || body.size() == 0) {
            try {

                request.getExchange().sendResponseHeaders(this.status, -1);
                LOGGER.fine(() -> "Sent empty response, status=" + status);
                return;

            } catch (IOException exception) {
                throw new ApplicationException(Errors.RESPONSE_SEND_ERROR, exception);
            }
        }

        String json = HttpUtils.GSON.toJson(body);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        try {

            request.getExchange().sendResponseHeaders(this.status, bytes.length);

            try (OutputStream outputStream = request.getExchange().getResponseBody()) {
                outputStream.write(bytes);
            }

            LOGGER.fine(() -> "Sent response, status=" + status + ", bytes=" + bytes.length);

        } catch (IOException exception) {
            throw new ApplicationException(Errors.RESPONSE_SEND_ERROR, exception);
        }
    }
}
