package dev.felippevaz.http;

import com.sun.net.httpserver.HttpExchange;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class HttpAdapter {

    private static final Logger LOGGER = Logger.getLogger(HttpAdapter.class.getName());

    public static final long DEFAULT_MAX_BODY_BYTES = 1_048_576; // 1MB

    private HttpAdapter() {
    }

    public static HttpRequest toRequest(HttpExchange exchange) throws IOException {
        return toRequest(exchange, DEFAULT_MAX_BODY_BYTES);
    }

    public static HttpRequest toRequest(HttpExchange exchange, long maxBodyBytes) throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();

        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty())
                headers.put(key, values.get(0));
        });

        long declaredLength = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));

        if (declaredLength > maxBodyBytes) {
            LOGGER.warning(() -> "Rejecting " + method + " " + path
                    + ": declared Content-Length " + declaredLength + " exceeds limit of " + maxBodyBytes + " bytes");
            throw new ApplicationException(Errors.PAYLOAD_TOO_LARGE, null);
        }

        byte[] body = readBody(exchange.getRequestBody(), method, path, maxBodyBytes);

        InetAddress remoteAddress = exchange.getRemoteAddress() != null
                ? exchange.getRemoteAddress().getAddress()
                : null;

        LOGGER.fine(() -> "Parsed " + method + " " + path + " (" + body.length + " bytes body)");

        return new HttpRequest(method, path, query, headers, body, remoteAddress, exchange);
    }

    private static long parseContentLength(String value) {

        if (value == null)
            return -1;

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static byte[] readBody(InputStream inputStream, String method, String path, long maxBodyBytes) throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        long total = 0;

        // Content-Length pode estar ausente (chunked) ou ser forjado, então o limite
        // real é imposto aqui, durante a leitura, e não apenas no header declarado acima.
        try (InputStream input = inputStream) {

            int n;
            while ((n = input.read(buffer)) != -1) {

                total += n;

                if (total > maxBodyBytes) {
                    LOGGER.warning(() -> "Aborting read of " + method + " " + path
                            + ": body exceeded limit of " + maxBodyBytes + " bytes");
                    throw new ApplicationException(Errors.PAYLOAD_TOO_LARGE, null);
                }

                output.write(buffer, 0, n);
            }
        }

        return output.toByteArray();
    }
}
