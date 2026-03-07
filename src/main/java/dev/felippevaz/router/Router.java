package dev.felippevaz.router;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Router {

    private final List<Route> routes = new ArrayList<>();

    public void registerRoute(Route route) {
        this.routes.add(route);
    }

    public RouteMatch findRoute(String method, String path) {

        for (Route route : routes) {

            if(!route.getMethod().equalsIgnoreCase(method))
                continue;

            Pattern pattern = Pattern.compile(route.getRegexPath());
            Matcher matcher = pattern.matcher(path);

            if(matcher.matches()) {

                List<String> parameters = new ArrayList<>();

                for (int i = 1; i <= matcher.groupCount(); i++)
                    parameters.add(matcher.group(i));

                return new RouteMatch(route.getHandler(), route.getController(), parameters);
            }
        }

        return null;
    }
}
