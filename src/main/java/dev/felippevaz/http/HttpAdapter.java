package dev.felippevaz.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HttpAdapter {

    public static HttpRequest toRequest(HttpExchange exchange) throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((key, value) -> {
            headers.put(key, value.get(0));
        });

        String body = readBody(exchange.getRequestBody());

        return new HttpRequest(method, path, headers, body, exchange);
    }

    private static String readBody(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];

        Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        int n;

        while ((n = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, n);
        }

        return sb.toString();
    }
}
