



package com.haoran.music.common.filter;

import com.haoran.music.common.config.MusicUploadConfig;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class EmojiUploadRequestSizeFilter extends OncePerRequestFilter {

    private static final long MULTIPART_OVERHEAD_BYTES = 1024L * 1024L;
    private final MusicUploadConfig config;

    public EmojiUploadRequestSizeFilter(MusicUploadConfig config) {
        this.config = config;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().matches(".*/emoji/package/[^/]+/emojis/upload$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long maximum = config.getEmojiMaxFileSize() * config.getEmojiMaxFiles()
                + MULTIPART_OVERHEAD_BYTES;
        long contentLength = request.getContentLengthLong();
        if (contentLength > maximum) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":413,\"message\":\"上传内容超过表情批次上限\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
