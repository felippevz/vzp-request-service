package dev.felippevaz.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.felippevaz.annotations.Controller;
import dev.felippevaz.annotations.Get;
import dev.felippevaz.annotations.Post;
import dev.felippevaz.controller;
import dev.felippevaz.http.HttpAdapter;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpResponse;
import dev.felippevaz.router.Route;
import dev.felippevaz.router.Router;

import java.io.IOException;
import java.lang.reflect.Method;

public class RequestHandler implements HttpHandler {

    private final Router router;

    public RequestHandler(Router router) {
        this.router = router;
    }

    public void registerController(Object controller) {

        Class<?> controllerClass = controller.getClass();

        if(!controllerClass.isAnnotationPresent(Controller.class))
            return;

        String basePath = controllerClass.getAnnotation(Controller.class).value();

        for (Method method : controllerClass.getDeclaredMethods()) {

            if(method.isAnnotationPresent(Get.class)) {
                Route route = new Route("GET", basePath, controller, method);
                this.router.registerRoute(route);
            }

            if(method.isAnnotationPresent(Post.class)) {
                Route route = new Route("POST", basePath, controller, method);
                this.router.registerRoute(route);
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        HttpRequest request = HttpAdapter.toRequest(exchange);

        Route route = router.findRoute(request.getMethod(), request.getPath());

        if (route == null) {

            //treat error later
            new HttpResponse().send(exchange);
            return;
        }

        try {

            Object controller = route.getController();
            Method handler = route.getHandler();


            if(handler.getParameterCount() == 0)
                handler.invoke(controller);
            else
                handler.invoke(controller, request);


            //treat error later
            new HttpResponse().send(exchange);

        } catch (Exception e) {

            //treat error later
            new HttpResponse().send(exchange);

            e.printStackTrace();
        }
    }
}
