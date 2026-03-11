package dev.felippevaz.http;

import dev.felippevaz.exceptions.Errors;

public class HttpUtils {

    public static void ok(HttpRequest request) {

        HttpResponse response = new HttpResponse();

        response.setStatus(200);

        response.clearFields();

        response.send(request);
    }

    public static void send(Errors error, HttpRequest request) {

        HttpResponse response = new HttpResponse();

        response.setStatus(error.getHttpCode())
                .addFieldBody("error", error.getMessage());

        response.send(request);
    }
}
