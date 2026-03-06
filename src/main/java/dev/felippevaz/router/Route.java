package dev.felippevaz.router;

import java.lang.reflect.Method;

public class Route {

    private final String method;
    private final String path;
    private final Object controller;
    private final Method handler;


    public Route(String method, String path, Object controller, Method handler) {
        this.method = method;
        this.path = path;
        this.controller = controller;
        this.handler = handler;
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
}
