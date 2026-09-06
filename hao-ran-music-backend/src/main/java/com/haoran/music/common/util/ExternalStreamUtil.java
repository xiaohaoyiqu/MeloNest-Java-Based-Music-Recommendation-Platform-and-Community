


package com.haoran.music.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;




public final class ExternalStreamUtil {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 30000;

    private static final int BUFFER_SIZE = 8192;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private ExternalStreamUtil() {
    }

    public static HttpURLConnection openGetConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    public static void applyBrowserHeaders(HttpURLConnection connection, boolean includeReferer) {
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Accept-Encoding", "identity");
        connection.setRequestProperty("Connection", "keep-alive");
        if (includeReferer) {
            connection.setRequestProperty("Referer", "https://music.163.com/");
        }
    }

    public static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
        output.flush();
    }
}
