package dev.felippevaz.http;

import com.google.gson.Gson;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {

    private static final Gson GSON = new Gson();

    private int status = 200;
    private final Map<String, String> body = new HashMap<>();
    private final Map<String, String> headers = new HashMap<>();
    private boolean sent = false;

    public HttpResponse() {
        this.setHeader("Content-Type", "application/json");
        this.body.put("timestamp", "" + System.currentTimeMillis());
    }

    public HttpResponse setStatus(int status) {
        this.status = status;
        return this;
    }

    public void clearFields() {
        this.body.clear();
    }

    public HttpResponse addFieldBody(String key, String value) {
        this.body.put(key, value);
        return this;
    }

    public HttpResponse setHeader(String key, String value) {
        this.headers.put(key, value);
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

        String json = GSON.toJson(body);

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
