package dev.felippevaz;

import dev.felippevaz.annotations.Controller;
import dev.felippevaz.annotations.Get;
import dev.felippevaz.annotations.Post;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpResponse;

@Controller("/test")
public class controller {

    @Get("/teste/{id}/{profile}")
    public void get(HttpRequest request, String id, String profile) {
        System.out.println("cc");
    }

    @Get
    public void genericGet(HttpRequest request) {

        HttpResponse response = new HttpResponse();

        response.clearFields(); // <- By default, the "timestamp" field is already created

        response.setStatus(200)
                .setHeader("Content-Type", "application/json") // <- By default, the "application/json" context is already added
                .addFieldBody("field", "value")
                .addFieldBody("message", "the response was created"); // <- create as many fields as you want.

        response.send(request); // <- Response sent successfully!
    }

    @Post
    public void putId(HttpRequest request) {
        System.out.println("bb");
    }
}
