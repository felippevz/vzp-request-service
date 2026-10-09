package dev.felippevaz.router;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;

public class Router {

    private final List<Route> routes = new CopyOnWriteArrayList<>();

    public void registerRoute(Route route) {
        this.routes.add(route);
    }

    public RouteMatch findRoute(String method, String path) {

        for (Route route : routes) {

            if(!route.getMethod().equalsIgnoreCase(method))
                continue;

            Matcher matcher = route.getPattern().matcher(path);

            if(matcher.matches()) {

                List<String> parameters = new ArrayList<>();

                for (int i = 1; i <= matcher.groupCount(); i++)
                    parameters.add(matcher.group(i));

                return new RouteMatch(route.getHandler(), route.getController(), parameters, route.isPublic());
            }
        }

        return null;
    }
}
