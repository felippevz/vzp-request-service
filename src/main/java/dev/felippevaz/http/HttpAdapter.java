package dev.felippevaz.http;

import com.sun.net.httpserver.HttpExchange;
import dev.felippevaz.exceptions.ApplicationException;
import dev.felippevaz.exceptions.Errors;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class HttpAdapter {

    private static final Logger LOGGER = Logger.getLogger(HttpAdapter.class.getName());

    private static volatile long maxBodyBytes = 1_048_576; // 1MB default

    public static void setMaxBodyBytes(long maxBodyBytes) {
        HttpAdapter.maxBodyBytes = maxBodyBytes;
    }

    public static HttpRequest toRequest(HttpExchange exchange) throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

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

        String body = readBody(exchange.getRequestBody(), method, path);

        LOGGER.fine(() -> "Parsed " + method + " " + path + " (" + body.length() + " chars body)");

        return new HttpRequest(method, path, headers, body, exchange);
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

    private static String readBody(InputStream inputStream, String method, String path) throws IOException {

        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        long total = 0;

        // Content-Length pode estar ausente (chunked) ou ser forjado, então o limite
        // real é imposto aqui, byte a byte, e não apenas no header declarado acima.
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {

            int n;
            while ((n = reader.read(buffer)) != -1) {

                total += n;

                if (total > maxBodyBytes) {
                    LOGGER.warning(() -> "Aborting read of " + method + " " + path
                            + ": body exceeded limit of " + maxBodyBytes + " bytes");
                    throw new ApplicationException(Errors.PAYLOAD_TOO_LARGE, null);
                }

                sb.append(buffer, 0, n);
            }
        }

        return sb.toString();
    }
}
