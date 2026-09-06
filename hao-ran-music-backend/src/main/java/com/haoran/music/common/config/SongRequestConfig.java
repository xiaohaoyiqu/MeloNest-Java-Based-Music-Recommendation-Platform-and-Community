package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;






@Data
@Component
@ConfigurationProperties(prefix = "song.request")
public class SongRequestConfig {

    private Upload upload = new Upload();
    private Nginx nginx = new Nginx();
    private Node2 node2 = new Node2();
    private Limits limits = new Limits();
    private Sync sync = new Sync();

    public boolean isAllowedFormat(String extension) {
        if (extension == null) {
            return false;
        }
        for (String allowedFormat : getLimits().getAllowedFormats()) {
            if (extension.equalsIgnoreCase(allowedFormat)) {
                return true;
            }
        }
        return false;
    }

    public Upload getUpload() {
        if (upload == null) {
            upload = new Upload();
        }
        return upload;
    }

    public Nginx getNginx() {
        if (nginx == null) {
            nginx = new Nginx();
        }
        return nginx;
    }

    public Node2 getNode2() {
        if (node2 == null) {
            node2 = new Node2();
        }
        return node2;
    }

    public Limits getLimits() {
        if (limits == null) {
            limits = new Limits();
        }
        return limits;
    }

    public Sync getSync() {
        if (sync == null) {
            sync = new Sync();
        }
        return sync;
    }

    @Data
    public static class Upload {
        private String path;
    }

    @Data
    public static class Nginx {
        private String url;
    }

    @Data
    public static class Node2 {
        private String host;
        private String user;
        private String path;
    }

    @Data
    public static class Limits {
        private int dailyMaxRequests = 20;
        private long maxFileSize = 500L * 1024 * 1024;
        private List<String> allowedFormats = new ArrayList<>(Arrays.asList(
                "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape"
        ));
    }

    @Data
    public static class Sync {
        private int mkdirTimeoutSeconds = 30;
        private int scpTimeoutSeconds = 600;
    }
}
