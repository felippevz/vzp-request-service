package dev.felippevaz.http;

import com.sun.net.httpserver.HttpExchange;

import java.util.Map;

public class HttpRequest {

    private final String method;
    private final String path;
    private final Map<String, String> headers;
    private final String body;
    private final HttpExchange exchange;


    public HttpRequest(String method, String path, Map<String, String> headers, String body, HttpExchange exchange) {
        this.method = method;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.exchange = exchange;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public HttpExchange getExchange() {
        return this.exchange;
    }
}
