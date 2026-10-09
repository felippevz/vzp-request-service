package dev.felippevaz.router;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

public class Route {

    private final String method;
    private final String regexPath;
    private final String path;
    private final Object controller;
    private final Method handler;
    private final Pattern pattern;
    private final boolean publicRoute;

    public Route(String method, String regexPath, String path, Object controller, Method handler) {
        this(method, regexPath, path, controller, handler, false);
    }

    public Route(String method, String regexPath, String path, Object controller, Method handler, boolean publicRoute) {
        this.method = method;
        this.regexPath = regexPath;
        this.path = path;
        this.controller = controller;
        this.handler = handler;
        this.pattern = Pattern.compile(regexPath);
        this.publicRoute = publicRoute;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public Object getController() {
        return controller;
    }

    public Method getHandler() {
        return handler;
    }

    public String getRegexPath() {
        return this.regexPath;
    }

    public Pattern getPattern() {
        return this.pattern;
    }

    public boolean isPublic() {
        return this.publicRoute;
    }
}
