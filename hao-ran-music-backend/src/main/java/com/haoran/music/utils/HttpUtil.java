package com.haoran.music.utils;

import com.haoran.music.common.util.ObjectUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;






@Slf4j
public final class HttpUtil {
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_REQUEST_BYTES = 64 * 1024;

    private HttpUtil() {
    }










    public static String getJson(String urlString, int timeout, int maxResponseBytes,
                                 String... allowedHosts) {
        URI uri = validateTarget(urlString, allowedHosts);
        validateLimits(timeout, maxResponseBytes);
        return executeJson(uri, "GET", null, timeout, maxResponseBytes);
    }











    public static String postJson(String urlString, String jsonBody, int timeout, int maxResponseBytes,
                                  String... allowedHosts) {
        URI uri = validateTarget(urlString, allowedHosts);
        validateLimits(timeout, maxResponseBytes);
        byte[] requestBody = jsonBody == null ? new byte[0] : jsonBody.getBytes(StandardCharsets.UTF_8);
        if (requestBody.length == 0 || requestBody.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("invalid outbound JSON body size");
        }
        return executeJson(uri, "POST", requestBody, timeout, maxResponseBytes);
    }

    private static void validateLimits(int timeout, int maxResponseBytes) {
        if (timeout < 1 || timeout > 30000 || maxResponseBytes < 1 || maxResponseBytes > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("invalid outbound HTTP limits");
        }
    }

    private static String executeJson(URI uri, String method, byte[] requestBody,
                                      int timeout, int maxResponseBytes) {
        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", "HaoRanMusic/1.0");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (requestBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(requestBody);
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("upstream-http-" + responseCode);
            }
            int contentLength = connection.getContentLength();
            if (contentLength > maxResponseBytes) {
                throw new IllegalStateException("upstream-response-too-large");
            }
            try (InputStream input = connection.getInputStream()) {
                return readUtf8(input, maxResponseBytes);
            }
        } catch (Exception e) {
            String reason = e.getMessage() != null && e.getMessage().startsWith("upstream-")
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            log.warn("event=fixed_upstream_request_failed host={} reason={}", uri.getHost(), reason);
            throw new IllegalStateException("上游请求失败", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }









    static String readUtf8(InputStream input, int maxResponseBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxResponseBytes, BUFFER_SIZE));
        byte[] buffer = new byte[BUFFER_SIZE];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseBytes) {
                throw new IllegalStateException("upstream-response-too-large");
            }
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }








    private static URI validateTarget(String urlString, String... allowedHosts) {
        if (ObjectUtils.isEmpty(urlString) || allowedHosts == null || allowedHosts.length == 0) {
            throw new IllegalArgumentException("outbound target is empty");
        }
        URI uri = URI.create(urlString.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("outbound target is not allowed");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = Arrays.stream(allowedHosts)
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
        if (!allowed) {
            throw new IllegalArgumentException("outbound host is not allowed");
        }
        return uri;
    }
}
