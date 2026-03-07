package dev.felippevaz.router;

import java.lang.reflect.Method;
import java.util.List;

public class RouteMatch {

    private final Method method;
    private final Object controller;
    private final List<String> parameters;

    public RouteMatch(Method method, Object controller, List<String> parameters) {
        this.method = method;
        this.controller = controller;
        this.parameters = parameters;
    }

    public Method getMethod() {
        return method;
    }

    public Object getController() {
        return controller;
    }

    public List<String> getParameters() {
        return parameters;
    }
}
