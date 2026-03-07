package dev.felippevaz.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.felippevaz.annotations.*;
import dev.felippevaz.http.HttpAdapter;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpResponse;
import dev.felippevaz.router.Route;
import dev.felippevaz.router.RouteMatch;
import dev.felippevaz.router.Router;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        Map<Class<? extends Annotation>, String> httpMethods = new HashMap<>();

        httpMethods.put(Get.class, "GET");
        httpMethods.put(Post.class, "POST");
        httpMethods.put(Put.class, "PUT");
        httpMethods.put(Delete.class, "DELETE");
        httpMethods.put(Patch.class, "PATCH");

        for (Method method : controllerClass.getDeclaredMethods()) {

            for(Class<? extends Annotation> entry : httpMethods.keySet()) {

                if(method.isAnnotationPresent(entry)) {

                    Annotation annotation = method.getAnnotation(entry);

                    try {

                        String value = (String) entry.getMethod("value").invoke(annotation);
                        String fullPath = basePath + value;
                        String regexPath = createRegexPath(fullPath);

                        Route route = new Route(httpMethods.get(entry), regexPath, fullPath, controller, method);
                        this.router.registerRoute(route);

                    } catch (Exception e) {
                        //todo: treat error later
                    }
                }
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        HttpRequest request = HttpAdapter.toRequest(exchange);

        RouteMatch match = router.findRoute(request.getMethod(), request.getPath());

        if (match == null) {

            //todo: treat error later
            new HttpResponse().send(request);
            return;
        }

        try {

            Object controller = match.getController();
            Method method = match.getMethod();

            List<String> values = match.getParameters();

            Parameter[] parameters = method.getParameters();

            Object[] args = new Object[parameters.length];

            args[0] = request;


            for (int i = 1; i < parameters.length; i++)
                args[i] = values.get(i-1);

            if(method.getParameterCount() == 0)
                method.invoke(controller, args);
            else
                method.invoke(controller, args);


            //todo: treat error later
            new HttpResponse().send(request);

        } catch (Exception e) {

            //todo: treat error later
            new HttpResponse().send(request);
        }
    }

    private String createRegexPath(String path) {
        return path.replaceAll("\\{[^/]+}", "([^/]+)") + "$";
    }
}
