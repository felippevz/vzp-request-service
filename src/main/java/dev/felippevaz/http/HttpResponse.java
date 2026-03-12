package dev.felippevaz.http;

import com.google.gson.JsonObject;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpResponse {

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

    public HttpResponse addListObjects(String property, List<Object> objects) {
        this.body.add(property, HttpUtils.GSON.toJsonTree(objects));
        return this;
    }

    public void send(HttpRequest request) {

        if(this.sent)
            return;

        if(body.isEmpty()) {
            try {

                request.getExchange().sendResponseHeaders(this.status, -1);
                return;

            } catch (IOException exception) {
                throw new ApplicationException(Errors.RESPONSE_SEND_ERROR, exception);
            }
        }

        this.headers.forEach((k, v) ->
                request.getExchange().getResponseHeaders().add(k, v)
        );

        String json = body.getAsString();

        byte[] bytes = json.getBytes();

        try(OutputStream outputStream = request.getExchange().getResponseBody()) {

            request.getExchange().sendResponseHeaders(this.status, bytes.length);

            outputStream.write(bytes);

        } catch (IOException exception) {
            throw new ApplicationException(Errors.RESPONSE_SEND_ERROR, exception);
        }

        sent = true;
    }
}
