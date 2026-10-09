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
import dev.felippevaz.security.Authenticator;
import dev.felippevaz.security.IpAllowList;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RequestHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(RequestHandler.class.getName());

    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{[^/]+?}");

    private final Router router;
    private final IpAllowList allowList;

    private volatile Authenticator authenticator;
    private volatile long maxBodyBytes = HttpAdapter.DEFAULT_MAX_BODY_BYTES;

    public RequestHandler(Router router) {
        this(router, new IpAllowList());
    }

    public RequestHandler(Router router, IpAllowList allowList) {
        this.router = router;
        this.allowList = allowList;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public void setMaxBodyBytes(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    public void registerController(Object controller) {

        Class<?> controllerClass = controller.getClass();

        if(!controllerClass.isAnnotationPresent(Controller.class))
            return;

        String basePath = controllerClass.getAnnotation(Controller.class).value();
        boolean publicController = controllerClass.isAnnotationPresent(Public.class);

        Map<Class<? extends Annotation>, String> httpMethods = new LinkedHashMap<>();

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
                        boolean publicRoute = publicController || method.isAnnotationPresent(Public.class);

                        method.setAccessible(true);

                        Route route = new Route(httpMethods.get(entry), regexPath, fullPath, controller, method, publicRoute);
                        this.router.registerRoute(route);

                        String logMethod = httpMethods.get(entry);
                        LOGGER.fine(() -> "Registered route " + logMethod + " " + fullPath + (publicRoute ? " (public)" : ""));

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

            // A allowlist é checada antes de ler o corpo: um IP recusado não
            // consome memória nem chega perto de um controller.
            InetAddress remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().getAddress() : null;

            if (!allowList.isAllowed(remote)) {
                LOGGER.warning(() -> "Rejected " + exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                        + " from " + (remote != null ? remote.getHostAddress() : "unknown") + ": IP not allowed");
                sendSafely(null, exchange, Errors.FORBIDDEN);
                return;
            }

            request = HttpAdapter.toRequest(exchange, maxBodyBytes);

            final HttpRequest currentRequest = request;
            LOGGER.fine(() -> "Dispatching " + currentRequest.getMethod() + " " + currentRequest.getPath());

            RouteMatch match = router.findRoute(request.getMethod(), request.getPath());

            if (match == null) {
                LOGGER.fine(() -> "No route found for " + currentRequest.getMethod() + " " + currentRequest.getPath());
                HttpUtils.send(Errors.ROUTE_NOT_FOUND, request);
                return;
            }

            Authenticator currentAuthenticator = this.authenticator;

            if (currentAuthenticator != null && !match.isPublic())
                currentAuthenticator.authenticate(request);

            invoke(match, request);

            // Se o controller não respondeu nada, devolve 200 vazio. Se já respondeu,
            // isto é ignorado (HttpResponse só envia uma vez por requisição).
            HttpUtils.ok(request);

        } catch (ApplicationException appException) {

            String description = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();

            // Erros 4xx são esperados (cliente errado, token inválido, rota inexistente):
            // logar stacktrace para cada um só gera ruído.
            if (appException.getHttpCode() < 500)
                LOGGER.info(() -> "Client error handling " + description + ": " + appException.getMessage());
            else
                LOGGER.log(Level.WARNING, "Application error handling " + description, appException);

            sendSafely(request, exchange, appException.getError());

        } catch (Throwable throwable) {

            // Qualquer falha não prevista (NPE, erro de biblioteca, Error, etc.) cai aqui.
            // Sem este catch-all a exceção escapa para o com.sun.net.httpserver, que a
            // engole silenciosamente (log em nível TRACE, sem handler configurado) e
            // apenas fecha a conexão sem responder ao cliente.
            LOGGER.log(Level.SEVERE, "Unhandled error handling " + exchange.getRequestMethod()
                    + " " + exchange.getRequestURI().getPath(), throwable);

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
            byte[] body = ("{\"error\":\"" + error.getMessage() + "\",\"code\":\"" + error.getInternalCode() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
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

    // Trechos literais do caminho são escapados ("." não vira "qualquer caractere");
    // só os segmentos {param} viram grupos de captura.
    private String createRegexPath(String path) {

        Matcher matcher = PATH_PARAMETER.matcher(path);
        StringBuilder regex = new StringBuilder();
        int last = 0;

        while (matcher.find()) {
            regex.append(Pattern.quote(path.substring(last, matcher.start()))).append("([^/]+)");
            last = matcher.end();
        }

        regex.append(Pattern.quote(path.substring(last)));

        return regex.toString();
    }
}
