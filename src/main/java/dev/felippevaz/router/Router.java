package dev.felippevaz.router;

import java.util.ArrayList;
import java.util.List;

public class Router {

    private final List<Route> routes = new ArrayList<>();

    public void registerRoute(Route route) {
        this.routes.add(route);
    }

    public Route findRoute(String method, String path) {

        for (Route route : routes) {

            if(!route.getMethod().equalsIgnoreCase(method))
                continue;

            if(route.getPath().equals(path))
                return route;
        }

        return null;
    }
}
