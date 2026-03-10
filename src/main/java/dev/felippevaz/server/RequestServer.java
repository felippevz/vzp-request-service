package dev.felippevaz.server;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.handler.RequestHandler;
import dev.felippevaz.router.Router;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RequestServer {

    private final int port;
    private final RequestHandler requestHandler;
    private Executor executor;
    private int backLog;
    private SSLContext sslContext;
    private HttpsConfigurator configurator;

    public RequestServer(int port) {
        this.port = port;
        this.backLog = 0;
        this.requestHandler = new RequestHandler(new Router());
        this.executor = Executors.newFixedThreadPool(1);
        this.sslContext = getDefaultSSLContext();
        this.configurator = new HttpsConfigurator(this.sslContext);
    }

    public void registerController(Object controller) {
        this.requestHandler.registerController(controller);
    }

    public void start() {

        try {

            HttpsServer server = HttpsServer.create(new InetSocketAddress(this.port), this.backLog);

            server.setHttpsConfigurator(this.configurator);

            server.setExecutor(this.executor);
            server.createContext("/", this.requestHandler);

            server.start();

        } catch (IOException exception) {

            throw new ApplicationException(Errors.SERVER_INIT_ERROR, exception);
        }
    }

    public void setHttpsConfigurator(HttpsConfigurator configurator) {
        this.configurator = configurator;
    }

    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
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

    private SSLContext getDefaultSSLContext() {

        SSLContext context;

        try {

            char[] password = "password".toCharArray();

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(
                    getClass().getResourceAsStream("/keys/test-keystore.jks"),
                    password
            );

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

            keyManagerFactory.init(keyStore, password);

            context = SSLContext.getInstance("TLS");
            context.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

            return context;

        } catch (Exception e){
            throw new RuntimeException("error in certificate");
        }
    }
}
