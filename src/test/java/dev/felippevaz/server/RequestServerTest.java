package dev.felippevaz.server;

import dev.felippevaz.annotations.Controller;
import dev.felippevaz.annotations.Get;
import dev.felippevaz.annotations.Post;
import dev.felippevaz.annotations.Public;
import dev.felippevaz.client.RestClient;
import dev.felippevaz.client.RestResponse;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.http.HttpRequest;
import dev.felippevaz.http.HttpResponse;
import dev.felippevaz.http.HttpUtils;
import dev.felippevaz.logging.LoggingConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class RequestServerTest {

    private static final String TOKEN = "secret";

    private static RequestServer server;
    private static RestClient client;
    private static RestClient anonymousClient;
    private static final RecordingHandler LOGS = new RecordingHandler();

    public static class Item {
        String name;
        int amount;
    }

    @Public
    @Controller("/public")
    public static class PublicController {

        @Get("/ping")
        public void ping(HttpRequest request) {
            new HttpResponse().addFieldBody("pong", "true").send(request);
        }
    }

    @Controller("/api")
    public static class ApiController {

        @Public
        @Get("/health")
        public void health(HttpRequest request) {
            new HttpResponse().addFieldBody("status", "UP").send(request);
        }

        @Get("/items/{id}")
        public void find(HttpRequest request, String id) {
            new HttpResponse()
                    .addFieldBody("id", id)
                    .addFieldBody("q", request.getQueryParameter("q"))
                    .send(request);
        }

        @Post("/items")
        public void create(HttpRequest request) {
            Item item = request.getObjectBody(Item.class);
            new HttpResponse().setStatus(201).addObject("item", item).send(request);
        }

        @Get("/headers")
        public void headers(HttpRequest request) {
            new HttpResponse().addFieldBody("custom", request.getHeader("x-custom-header")).send(request);
        }

        @Get("/conflict")
        public void conflict(HttpRequest request) {
            new HttpResponse().setStatus(409).addFieldBody("status", "PLAYER_OFFLINE").send(request);
        }

        @Get("/silent")
        public void silent(HttpRequest request) {
            // Não responde nada: o handler devolve 200 vazio.
        }

        @Get("/page")
        public void page(HttpRequest request) {
            HttpUtils.sendRaw(request, 200, "text/html; charset=utf-8",
                    "<h1>Olá</h1>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Get("/file.json")
        public void file(HttpRequest request) {
            new HttpResponse().addFieldBody("file", "ok").send(request);
        }

        @Get("/domain-error")
        public void domainError(HttpRequest request) {
            throw new ApplicationException(Errors.ENTITY_NOT_FOUND, null);
        }

        @Get("/crash")
        public void crash(HttpRequest request) {
            throw new IllegalStateException("boom");
        }

        @Post("/signed")
        public void signed(HttpRequest request) throws Exception {
            String expected = sha256(request.getRawBody());
            new HttpResponse()
                    .addFieldBody("matches", String.valueOf(expected.equals(request.getHeader("X-Body-Hash"))))
                    .send(request);
        }
    }

    @BeforeAll
    static void startServer() {

        Logger.getLogger(LoggingConfig.getRootLoggerName()).addHandler(LOGS);

        server = new RequestServer(0);
        server.setBindAddress("127.0.0.1");
        server.setMaxRequestBodyBytes(256);
        server.setAuthenticator(request -> {
            if (!TOKEN.equals(request.getHeader("X-Token")))
                throw new ApplicationException(Errors.UNAUTHORIZED, null);
        });
        server.registerController(new PublicController());
        server.registerController(new ApiController());
        server.start();

        String baseUrl = "http://127.0.0.1:" + server.getPort();

        client = RestClient.builder(baseUrl).header("X-Token", TOKEN).build();
        anonymousClient = RestClient.builder(baseUrl).build();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
        Logger.getLogger(LoggingConfig.getRootLoggerName()).removeHandler(LOGS);
    }

    @BeforeEach
    void clearLogs() {
        LOGS.clear();
    }

    @Test
    void publicRoutesSkipTheAuthenticator() {
        RestResponse controllerLevel = anonymousClient.get("/public/ping");
        RestResponse methodLevel = anonymousClient.get("/api/health");

        assertEquals(200, controllerLevel.getStatus());
        assertEquals("true", controllerLevel.as(Body.class).pong);
        assertEquals(200, methodLevel.getStatus());
    }

    @Test
    void protectedRoutesRequireAuthentication() {
        RestResponse response = anonymousClient.get("/api/items/1");

        assertEquals(401, response.getStatus());
        assertEquals("ERROR_1012", response.as(Body.class).code);
    }

    @Test
    void pathAndQueryParameters() {
        RestResponse response = client.get("/api/items/42?q=hello%20world");
        Body body = response.as(Body.class);

        assertEquals(200, response.getStatus());
        assertEquals("42", body.id);
        assertEquals("hello world", body.q);
    }

    @Test
    void jsonBodyRoundTrip() {
        Item item = new Item();
        item.name = "Espada";
        item.amount = 2;

        RestResponse response = client.post("/api/items", item);

        assertEquals(201, response.getStatus());
        assertTrue(response.getBody().contains("\"name\":\"Espada\""));
    }

    @Test
    void malformedJsonIsBadRequest() {
        RestResponse response = client.post("/api/items", "{not json");

        assertEquals(400, response.getStatus());
    }

    @Test
    void headersAreCaseInsensitive() {
        RestResponse response = RestClient.builder(client.getBaseUrl())
                .header("X-Token", TOKEN)
                .header("X-CUSTOM-HEADER", "abc")
                .build()
                .get("/api/headers");

        assertEquals("abc", response.as(Body.class).custom);
    }

    @Test
    void customStatusFromController() {
        RestResponse response = client.get("/api/conflict");

        assertEquals(409, response.getStatus());
        assertEquals("PLAYER_OFFLINE", response.as(Body.class).status);
    }

    @Test
    void controllerWithoutResponseGetsEmpty200() {
        RestResponse response = client.get("/api/silent");

        assertEquals(200, response.getStatus());
        assertEquals("", response.getBody());
    }

    @Test
    void literalPathCharactersAreNotRegex() {
        assertEquals(200, client.get("/api/file.json").getStatus());
        assertEquals(404, client.get("/api/fileXjson").getStatus());
    }

    @Test
    void rawResponsesKeepTheirContentType() {
        RestResponse response = client.get("/api/page");

        assertEquals(200, response.getStatus());
        assertEquals("<h1>Olá</h1>", response.getBody());
        assertTrue(response.getHeader("Content-Type").startsWith("text/html"));
    }

    @Test
    void unknownRouteIs404() {
        RestResponse response = client.get("/api/nothing/here/at/all");

        assertEquals(404, response.getStatus());
        assertEquals("ERROR_1010", response.as(Body.class).code);
    }

    @Test
    void domainErrorKeepsItsStatus() {
        assertEquals(404, client.get("/api/domain-error").getStatus());
    }

    @Test
    void unexpectedErrorIs500WithoutLeakingDetails() {
        RestResponse response = client.get("/api/crash");

        assertEquals(500, response.getStatus());
        assertFalse(response.getBody().contains("boom"));
    }

    @Test
    void payloadTooLarge() {
        StringBuilder big = new StringBuilder("\"");
        for (int i = 0; i < 300; i++)
            big.append('a');
        big.append('"');

        assertEquals(413, client.post("/api/items", big.toString()).getStatus());
    }

    @Test
    void interceptorSeesExactBodyBytes() {
        RestClient signing = RestClient.builder(client.getBaseUrl())
                .header("X-Token", TOKEN)
                .interceptor(request -> {
                    try {
                        request.header("X-Body-Hash", sha256(request.getBodyBytes()));
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .build();

        RestResponse response = signing.post("/api/signed", "{\"nome\":\"ção\"}");

        assertEquals("true", response.as(Body.class).matches);
    }

    // Antes da correção, o ok() automático do RequestHandler tentava reenviar headers
    // depois do controller já ter respondido, gerando um erro em log a cada requisição.
    @Test
    void successfulRequestsDoNotLogWarnings() {
        client.get("/api/items/1");
        client.post("/api/items", "{\"name\":\"x\",\"amount\":1}");
        client.get("/api/silent");
        client.get("/api/page");
        anonymousClient.get("/public/ping");

        assertEquals(Collections.emptyList(), LOGS.messagesAtOrAbove(Level.WARNING));
    }

    @Test
    void allowListRejectsOtherIps() {
        RequestServer restricted = new RequestServer(0);
        restricted.setBindAddress("127.0.0.1");
        restricted.allowFrom("10.0.0.0/8");
        restricted.registerController(new PublicController());
        restricted.start();

        try {
            RestResponse response = RestClient.builder("http://127.0.0.1:" + restricted.getPort()).build().get("/public/ping");

            assertEquals(403, response.getStatus());
            assertEquals("ERROR_1013", response.as(Body.class).code);
        } finally {
            restricted.stop();
        }
    }

    @Test
    void stopReleasesThePortAndAllowsRestart() {
        RequestServer first = new RequestServer(0);
        first.setBindAddress("127.0.0.1");
        first.registerController(new PublicController());
        first.start();

        int port = first.getPort();
        first.stop();
        assertFalse(first.isRunning());

        // Mesma instância, mesma porta: o pool padrão é recriado.
        RequestServer second = new RequestServer(port);
        second.setBindAddress("127.0.0.1");
        second.registerController(new PublicController());
        second.start();

        try {
            assertEquals(200, RestClient.builder("http://127.0.0.1:" + port).build().get("/public/ping").getStatus());

            second.stop();
            second.start();

            assertEquals(200, RestClient.builder("http://127.0.0.1:" + port).build().get("/public/ping").getStatus());
        } finally {
            second.stop();
        }
    }

    static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }

    static class Body {
        String pong;
        String code;
        String id;
        String q;
        String custom;
        String status;
        String matches;
    }

    static class RecordingHandler extends Handler {

        private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        void clear() {
            records.clear();
        }

        List<String> messagesAtOrAbove(Level level) {
            List<String> messages = new ArrayList<>();
            synchronized (records) {
                for (LogRecord record : records)
                    if (record.getLevel().intValue() >= level.intValue())
                        messages.add(record.getLevel() + " " + record.getMessage());
            }
            return messages;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
