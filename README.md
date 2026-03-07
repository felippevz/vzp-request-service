# Request Server

Developed to be simple and lightweight, with the intention of creating and receiving requests from third parties
> Could be a **webhook endpoint** and request data

## How to use

Create class @Controller

````java

package dev.felippevaz;

import dev.felippevaz.annotations.Get;
import dev.felippevaz.annotations.Post;

@Controller("/path") //<--- Edit '/path' for anyway
public class GenericController {

    @Get
    public void genericGet() {
        //Implement yours services here
    }

    @Post
    public void genericPost() {
        //Implement yours services here
    }

    @Get("/{id}")
    public void genericGetId(String id) {
        //Implement yours services here
    }

    @Post("/example/{id}/{name}")
    public void genericPostId(String id, String name) {
        //Implement yours services here
    }
}

````

Create your RequestServer!

````java

import dev.felippevaz.server.RequestServer;

public class Main {

    static void main() {

        //Create object RequestServer
        RequestServer server = new RequestServer(8080); //<-- Edit server port for anyway
        
        //Register yours Controllers
        server.registerController(new GenericController());
        server.registerController(new ExampleController());
        
        //Starter Server HTTP!
        server.start();
    }
}

````

## How to send HttpResponse

Create HttpResponse

````java

import dev.felippevaz.annotations.Get;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpResponse;

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

````

The JSON result:

````json
{
    "field": "value",
    "message": "the response was created"
}
````