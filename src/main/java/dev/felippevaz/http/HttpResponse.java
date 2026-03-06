package dev.felippevaz.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;

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
        this.addHeader("Content-Type", "application/json");
        this.body.put("timestamp", "" + System.currentTimeMillis());
    }

    public HttpResponse setStatus(int status) {
        this.status = status;
        return this;
    }

    public HttpResponse addBody(String name, String message) {
        this.body.put(name, message);
        return this;
    }

    public HttpResponse addHeader(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public void send(HttpExchange exchange) throws IOException {

        if(this.sent)
            return;

        System.out.println(this.headers.get("Content-Type"));

        this.headers.forEach((k, v) ->
                exchange.getResponseHeaders().add(k, v)
        );

        String json = GSON.toJson(body);

        byte[] bytes = json.getBytes();

        exchange.sendResponseHeaders(this.status, bytes.length);

        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.close();

        sent = true;
    }
}
