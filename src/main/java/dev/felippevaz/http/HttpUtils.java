package dev.felippevaz.http;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.IOException;
import java.io.OutputStream;

public class HttpUtils {

    public static final Gson GSON = new Gson();

    public static void ok(HttpRequest request) {

        HttpResponse response = new HttpResponse();

        response.setStatus(200);

        response.clearFields();

        response.send(request);
    }

    /**
     * Envia uma resposta que não é JSON (HTML, CSS, JS, texto...). Como no
     * {@link HttpResponse#send}, só a primeira resposta de cada requisição é enviada.
     */
    public static void sendRaw(HttpRequest request, int status, String contentType, byte[] body) {

        if (request.isResponded())
            return;

        request.markResponded();

        HttpExchange exchange = request.getExchange();
        exchange.getResponseHeaders().set("Content-Type", contentType);

        try {
            if (body == null || body.length == 0) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }

            exchange.sendResponseHeaders(status, body.length);

            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } catch (IOException exception) {
            throw new ApplicationException(Errors.RESPONSE_SEND_ERROR, exception);
        }
    }

    public static void send(Errors error, HttpRequest request) {

        HttpResponse response = new HttpResponse();

        response.setStatus(error.getHttpCode())
                .addFieldBody("error", error.getMessage())
                .addFieldBody("code", error.getInternalCode());

        response.send(request);
    }
}
