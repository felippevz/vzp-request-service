package dev.felippevaz.server;

import com.sun.net.httpserver.HttpServer;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.handler.RequestHandler;
import dev.felippevaz.router.Router;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RequestServer {

    private final int port;
    private final RequestHandler requestHandler;
    private Executor executor;
    private int backLog;

    public RequestServer(int port) {
        this.port = port;
        this.backLog = 0;
        this.requestHandler = new RequestHandler(new Router());
        this.executor = Executors.newFixedThreadPool(1);
    }

    public void registerController(Object controller) {
        this.requestHandler.registerController(controller);
    }

    public void start() {

        Instant started = Instant.now();

        try {

            HttpServer server = HttpServer.create(new InetSocketAddress(this.port), this.backLog);

            server.setExecutor(this.executor);
            server.createContext("/", this.requestHandler);

            server.start();

            Instant finished = Instant.now();
            long duration = Duration.between(started, finished).toMillis();

            System.out.println("HttpServer started on port " + this.port);
            System.out.println("Time for initialization: " + duration + "ms");

        } catch (IOException exception) {

            throw new ApplicationException(Errors.SERVER_INIT_ERROR, exception);
        }
    }

    public void setBackLog(int backLog) {
        this.backLog = backLog;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public int getPort() {
        return this.port;
    }
}
