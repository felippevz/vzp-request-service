# Request Server

This project was developed with a focus on low operational cost, fast startup times, and architectural simplicity. The application is designed to handle low traffic volumes (approximately 100 to 300 requests per day). Therefore, a lightweight approach based on HttpsServer was adopted, avoiding full-blown frameworks like Spring Boot, which would introduce higher startup overhead and unnecessary complexity for this use case.

> Could be a **webhook endpoint** and request data

## How to use

Create class @Controller

````java

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

        //Security Setup
        server.setExecutor(Executors.EXECUTOR); //<-- Single-threaded default executor
        server.setBackLog(backLog); //<-- Default backlog set to 0 (will use the system default)
        server.setHttpsConfigurator(httpsConfigurator); // <-- WARNING: HttpsConfiguration has no default value.
        
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

JSON response:

````json
{
  "field": "value",
  "message": "the response was created"
}
````

Sending an empty HttpResponse

`````java

HttpUtils.ok(request); //<-- If no response is defined within the called method, this default response will be returned.

`````