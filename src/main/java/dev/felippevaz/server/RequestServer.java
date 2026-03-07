package dev.felippevaz.server;

import com.sun.net.httpserver.HttpServer;
import dev.felippevaz.handler.RequestHandler;
import dev.felippevaz.router.Router;

import java.io.IOException;
import java.net.InetSocketAddress;

public class RequestServer {

    private final int port;
    private final RequestHandler requestHandler;

    public RequestServer(int port) {
        this.port = port;
        Router router = new Router();
        this.requestHandler = new RequestHandler(router);
    }

    public void registerController(Object controller) {
        this.requestHandler.registerController(controller);
    }

    public void start() {

        try {

            HttpServer server = HttpServer.create(new InetSocketAddress(this.port), 0);

            server.setExecutor(null);
            server.createContext("/", requestHandler);

            server.start();

        } catch (IOException exception) {

            //todo: treat error later
        }
    }
}
