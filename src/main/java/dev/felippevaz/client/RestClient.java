package dev.felippevaz.client;

import com.google.gson.Gson;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;
import dev.felippevaz.http.HttpUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Cliente HTTP leve para JSON, sobre {@link HttpURLConnection} (Java 8, sem dependências).
 * <p>
 * As chamadas são síncronas: rode-as fora de threads sensíveis (ex.: a thread
 * principal de um servidor de jogo). Status 4xx/5xx são devolvidos no
 * {@link RestResponse}; só falhas de rede/IO lançam
 * {@link ApplicationException} ({@link Errors#CLIENT_REQUEST_ERROR}).
 *
 * <pre>{@code
 * RestClient client = RestClient.builder("https://api.exemplo.com")
 *         .connectTimeoutMillis(5_000)
 *         .readTimeoutMillis(10_000)
 *         .interceptor(request -> request.header("X-Api-Key", apiKey))
 *         .build();
 *
 * RestResponse response = client.post("/servers/register", payload);
 * }</pre>
 */
public class RestClient {

    private static final Logger LOGGER = Logger.getLogger(RestClient.class.getName());

    // HttpURLConnection não aceita PATCH.
    private static final Set<String> SUPPORTED_METHODS = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "DELETE"));

    private final String baseUrl;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long maxResponseBytes;
    private final Map<String, String> defaultHeaders;
    private final List<RequestInterceptor> interceptors;
    private final Gson gson;

    private RestClient(Builder builder) {
        this.baseUrl = builder.baseUrl.endsWith("/")
                ? builder.baseUrl.substring(0, builder.baseUrl.length() - 1)
                : builder.baseUrl;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.maxResponseBytes = builder.maxResponseBytes;
        this.defaultHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.defaultHeaders));
        this.interceptors = Collections.unmodifiableList(new ArrayList<>(builder.interceptors));
        this.gson = builder.gson;
    }

    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    public RestResponse get(String path) {
        return execute("GET", path, null);
    }

    public RestResponse post(String path, Object body) {
        return execute("POST", path, body);
    }

    public RestResponse put(String path, Object body) {
        return execute("PUT", path, body);
    }

    public RestResponse delete(String path) {
        return execute("DELETE", path, null);
    }

    /**
     * @param body objeto serializado em JSON; uma {@code String} é enviada como está; null = sem corpo.
     */
    public RestResponse execute(String method, String path, Object body) {

        String serialized = body == null ? null : body instanceof String ? (String) body : gson.toJson(body);

        return execute(new RestRequest(method, path, serialized));
    }

    public RestResponse execute(RestRequest request) {

        if (!SUPPORTED_METHODS.contains(request.getMethod()))
            throw new IllegalArgumentException("Unsupported HTTP method: " + request.getMethod());

        for (RequestInterceptor interceptor : interceptors)
            interceptor.intercept(request);

        HttpURLConnection connection = null;

        try {

            URL url = new URL(baseUrl + request.getPath());
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setUseCaches(false);
            // Redirecionamentos não são seguidos: uma requisição assinada não deve
            // ser reenviada para outro destino sem o cliente saber.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "vzp-request-service");

            for (Map.Entry<String, String> header : defaultHeaders.entrySet())
                connection.setRequestProperty(header.getKey(), header.getValue());

            for (Map.Entry<String, String> header : request.getHeaders().entrySet())
                connection.setRequestProperty(header.getKey(), header.getValue());

            if (request.getBody() != null) {

                byte[] bytes = request.getBodyBytes();

                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }

            int status = connection.getResponseCode();

            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = stream != null ? read(stream) : "";

            Map<String, String> headers = new HashMap<>();
            for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet())
                if (header.getKey() != null && header.getValue() != null && !header.getValue().isEmpty())
                    headers.put(header.getKey(), header.getValue().get(0));

            LOGGER.fine(() -> request.getMethod() + " " + request.getPath() + " -> " + status);

            return new RestResponse(status, responseBody, headers, gson);

        } catch (IOException exception) {
            LOGGER.fine(() -> request.getMethod() + " " + request.getPath() + " failed: " + exception);
            throw new ApplicationException(Errors.CLIENT_REQUEST_ERROR, exception);
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private String read(InputStream stream) throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long total = 0;

        try (InputStream input = stream) {

            int n;
            while ((n = input.read(buffer)) != -1) {

                total += n;

                if (total > maxResponseBytes)
                    throw new IOException("Response body exceeded limit of " + maxResponseBytes + " bytes");

                output.write(buffer, 0, n);
            }
        }

        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static class Builder {

        private final String baseUrl;
        private int connectTimeoutMillis = 5_000;
        private int readTimeoutMillis = 10_000;
        private long maxResponseBytes = 4L * 1024 * 1024;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private final List<RequestInterceptor> interceptors = new ArrayList<>();
        private Gson gson = HttpUtils.GSON;

        private Builder(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty())
                throw new IllegalArgumentException("baseUrl is required");
            this.baseUrl = baseUrl.trim();
        }

        public Builder connectTimeoutMillis(int millis) {
            this.connectTimeoutMillis = millis;
            return this;
        }

        public Builder readTimeoutMillis(int millis) {
            this.readTimeoutMillis = millis;
            return this;
        }

        public Builder maxResponseBytes(long bytes) {
            this.maxResponseBytes = bytes;
            return this;
        }

        public Builder header(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }

        public Builder interceptor(RequestInterceptor interceptor) {
            this.interceptors.add(interceptor);
            return this;
        }

        public Builder gson(Gson gson) {
            this.gson = gson;
            return this;
        }

        public RestClient build() {
            return new RestClient(this);
        }
    }
}
