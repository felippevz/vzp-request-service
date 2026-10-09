package dev.felippevaz.router;

import java.lang.reflect.Method;
import java.util.List;

public class RouteMatch {

    private final Method method;
    private final Object controller;
    private final List<String> parameters;
    private final boolean publicRoute;

    public RouteMatch(Method method, Object controller, List<String> parameters) {
        this(method, controller, parameters, false);
    }

    public RouteMatch(Method method, Object controller, List<String> parameters, boolean publicRoute) {
        this.method = method;
        this.controller = controller;
        this.parameters = parameters;
        this.publicRoute = publicRoute;
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

    public boolean isPublic() {
        return publicRoute;
    }
}
