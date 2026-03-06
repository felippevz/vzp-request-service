package dev.felippevaz.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

public class HttpResponseUtils {

    public static void ok(int status, HttpExchange exchange) throws IOException {

        HttpResponse httpResponse = new HttpResponse();

        httpResponse.setStatus(status);

        httpResponse.send(exchange);
    }

    public static void error(int status, String message, HttpExchange exchange) throws IOException{

        HttpResponse httpResponse = new HttpResponse();

        httpResponse.setStatus(status);
        httpResponse.addBody("error", message);

        httpResponse.send(exchange);
    }
}
