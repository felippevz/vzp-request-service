package dev.felippevaz.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.felippevaz.annotations.*;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.http.HttpAdapter;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpUtils;
import dev.felippevaz.router.Route;
import dev.felippevaz.router.RouteMatch;
import dev.felippevaz.router.Router;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(RequestHandler.class.getName());

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

                        String logMethod = httpMethods.get(entry);
                        LOGGER.fine(() -> "Registered route " + logMethod + " " + fullPath);

                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                        throw new ApplicationException(Errors.VALUE_METHOD_CONTROLLER_ERROR, exception);
                    }
                }
            }
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        HttpRequest request = null;

        try {

            request = HttpAdapter.toRequest(exchange);

            final HttpRequest currentRequest = request;
            LOGGER.fine(() -> "Dispatching " + currentRequest.getMethod() + " " + currentRequest.getPath());

            RouteMatch match = router.findRoute(request.getMethod(), request.getPath());

            if (match == null) {
                LOGGER.fine(() -> "No route found for " + currentRequest.getMethod() + " " + currentRequest.getPath());
                HttpUtils.send(Errors.ROUTE_NOT_FOUND, request);
                return;
            }

            invoke(match, request);

            HttpUtils.ok(request);

        } catch (ApplicationException appException) {

            LOGGER.log(Level.WARNING, "Application error handling " + exchange.getRequestMethod()
                    + " " + exchange.getRequestURI(), appException);

            sendSafely(request, exchange, appException.getError());

        } catch (Throwable throwable) {

            // Qualquer falha não prevista (NPE, erro de biblioteca, Error, etc.) cai aqui.
            // Sem este catch-all a exceção escapa para o com.sun.net.httpserver, que a
            // engole silenciosamente (log em nível TRACE, sem handler configurado) e
            // apenas fecha a conexão sem responder ao cliente.
            LOGGER.log(Level.SEVERE, "Unhandled error handling " + exchange.getRequestMethod()
                    + " " + exchange.getRequestURI(), throwable);

            sendSafely(request, exchange, Errors.INTERNAL_SERVER_ERROR);

        } finally {
            exchange.close();
        }
    }

    private void invoke(RouteMatch match, HttpRequest request) {

        Object controller = match.getController();
        Method method = match.getMethod();

        List<String> values = match.getParameters();
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        if(parameters.length >= 1)
            args[0] = request;

        for (int i = 1; i < parameters.length; i++)
            args[i] = values.get(i-1);

        try {

            method.invoke(controller, args);

        } catch (IllegalAccessException | InvocationTargetException exception) {

            Throwable cause = exception;

            if (exception instanceof InvocationTargetException && exception.getCause() != null)
                cause = exception.getCause();

            // Preserva o erro de domínio original (ex.: ENTITY_NOT_FOUND) em vez de
            // mascarar tudo como um erro genérico de invocação.
            if (cause instanceof ApplicationException)
                throw (ApplicationException) cause;

            throw new ApplicationException(Errors.METHOD_INVOKE_ERROR, cause);
        }
    }

    private void sendSafely(HttpRequest request, HttpExchange exchange, Errors error) {
        try {

            if (request != null) {
                HttpUtils.send(error, request);
                return;
            }

            // A falha ocorreu antes do HttpRequest existir (ex.: parsing do exchange),
            // então respondemos direto pelo HttpExchange.
            byte[] body = ("{\"error\":\"" + error.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(error.getHttpCode(), body.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }

        } catch (Throwable sendFailure) {
            // Cliente provavelmente já desconectou. Apenas loga, nunca relança:
            // isto evita um loop de erro-ao-tratar-erro.
            LOGGER.log(Level.WARNING, "Failed to send error response to client (connection likely closed)", sendFailure);
        }
    }

    private String createRegexPath(String path) {
        return path.replaceAll("\\{[^/]+}", "([^/]+)") + "$";
    }
}
