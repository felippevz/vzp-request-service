package dev.felippevaz.server;

import com.sun.net.httpserver.HttpServer;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.handler.RequestHandler;
import dev.felippevaz.http.HttpAdapter;
import dev.felippevaz.logging.LoggingConfig;
import dev.felippevaz.router.Router;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestServer {

    private static final Logger LOGGER = Logger.getLogger(RequestServer.class.getName());

    private final int port;
    private final RequestHandler requestHandler;
    private Executor executor;
    private int backLog;

    // Timeouts nativos do com.sun.net.httpserver (idle/leitura/escrita). Sem eles,
    // um cliente lento (ou um ataque Slowloris) pode prender uma worker thread
    // indefinidamente, o que é especialmente grave combinado com um pool pequeno.
    private int idleIntervalSeconds = 30;
    private int maxRequestSeconds = 30;
    private int maxResponseSeconds = 30;

    public RequestServer(int port) {
        LoggingConfig.configure();
        this.port = port;
        this.backLog = 0;
        this.requestHandler = new RequestHandler(new Router());
        this.executor = createDefaultExecutor();
    }

    // Pool limitado (nunca 1 thread) com fila com limite, ThreadFactory nomeada +
    // UncaughtExceptionHandler, e uma política de rejeição que loga em vez de
    // simplesmente descartar ou deixar crescer a fila sem limite até OOM.
    private Executor createDefaultExecutor() {

        AtomicInteger threadCount = new AtomicInteger(1);

        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "http-worker-" + threadCount.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, throwable) ->
                    LOGGER.log(Level.SEVERE, "Uncaught exception in thread " + t.getName(), throwable));
            return thread;
        };

        RejectedExecutionHandler rejectionHandler = (runnable, threadPoolExecutor) -> {
            LOGGER.warning("Request rejected: thread pool saturated (active=" + threadPoolExecutor.getActiveCount()
                    + ", queued=" + threadPoolExecutor.getQueue().size() + "). Running on caller thread.");
            if (!threadPoolExecutor.isShutdown())
                runnable.run();
        };

        return new ThreadPoolExecutor(
                8, 64,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                threadFactory,
                rejectionHandler
        );
    }

    public void registerController(Object controller) {
        this.requestHandler.registerController(controller);
    }

    public void start() {

        Instant started = Instant.now();

        configureNativeServerTimeouts();

        try {

            HttpServer server = HttpServer.create(new InetSocketAddress(this.port), this.backLog);

            server.setExecutor(this.executor);
            server.createContext("/", this.requestHandler);

            server.start();

            Instant finished = Instant.now();
            long duration = Duration.between(started, finished).toMillis();

            LOGGER.info("HttpServer started on port " + this.port);
            LOGGER.info("Time for initialization: " + duration + "ms");

        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Failed to start HTTP server on port " + this.port, exception);
            throw new ApplicationException(Errors.SERVER_INIT_ERROR, exception);
        }
    }

    // Propriedades específicas da implementação de referência do JDK
    // (sun.net.httpserver.*). Precisam ser definidas antes do HttpServer.create().
    private void configureNativeServerTimeouts() {
        System.setProperty("sun.net.httpserver.idleInterval", String.valueOf(idleIntervalSeconds));
        System.setProperty("sun.net.httpserver.maxReqTime", String.valueOf(maxRequestSeconds));
        System.setProperty("sun.net.httpserver.maxRspTime", String.valueOf(maxResponseSeconds));
    }

    public void setBackLog(int backLog) {
        this.backLog = backLog;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void setMaxRequestBodyBytes(long maxBodyBytes) {
        HttpAdapter.setMaxBodyBytes(maxBodyBytes);
    }

    public void setIdleIntervalSeconds(int idleIntervalSeconds) {
        this.idleIntervalSeconds = idleIntervalSeconds;
    }

    public void setMaxRequestSeconds(int maxRequestSeconds) {
        this.maxRequestSeconds = maxRequestSeconds;
    }

    public void setMaxResponseSeconds(int maxResponseSeconds) {
        this.maxResponseSeconds = maxResponseSeconds;
    }

    public int getPort() {
        return this.port;
    }
}
