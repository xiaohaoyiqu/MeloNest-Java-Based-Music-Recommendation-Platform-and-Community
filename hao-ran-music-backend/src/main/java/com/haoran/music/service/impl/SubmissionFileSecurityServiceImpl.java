


package com.haoran.music.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.service.MultiFileUploadService;
import com.haoran.music.service.SubmissionFileSecurityService;
import com.haoran.music.service.VirusScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;




@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionFileSecurityServiceImpl implements SubmissionFileSecurityService {

    private static final Set<String> ALBUM_ALLOWED_TYPES = new LinkedHashSet<>(Arrays.asList("audio", "lyric", "image"));
    private static final Set<String> SINGLE_ALLOWED_TYPES = Collections.singleton("audio");

    private final MultiFileUploadService multiFileUploadService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private VirusScanService virusScanService;

    @Value("${music.upload.multi-file.path}")
    private String multiFilePath;

    @Value("${music.upload.nginx.url}")
    private String multiFileNginxUrl;

    @Value("${music.upload.nginx-url-prefix:}")
    private String uploadNginxUrlPrefix;

    @Value("${music.upload.virus-scan-enabled:true}")
    private boolean virusScanEnabled;

    @Override
    public void validateSingleAudioFile(Long userId, String fileUrl, boolean scan) {
        if (StrUtil.isBlank(fileUrl)) {
            throw new IllegalArgumentException("single/remix submissions must include fileUrl");
        }
        validateUrl(userId, fileUrl, SINGLE_ALLOWED_TYPES, scan);
    }

    @Override
    public void validateAlbumFileUrls(Long userId, String fileUrls, boolean scan) {
        LinkedHashSet<String> urls = parseUrls(fileUrls);
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("album/EP submissions must include extracted fileUrls");
        }

        boolean hasAudio = false;
        for (String url : urls) {
            String type = identifyType(url);
            if ("audio".equals(type)) {
                hasAudio = true;
            }
            validateUrl(userId, url, ALBUM_ALLOWED_TYPES, scan);
        }
        if (!hasAudio) {
            throw new IllegalArgumentException("album/EP submissions must include at least one audio file");
        }
    }

    private void validateUrl(Long userId, String url, Set<String> allowedTypes, boolean scan) {
        if (userId == null) {
            throw new SecurityException("login is required for submission file validation");
        }
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("submission file URL is blank");
        }
        String type = identifyType(url);
        if (!allowedTypes.contains(type)) {
            throw new IllegalArgumentException("unsupported submission file type: " + type);
        }

        File file = resolveSubmissionFile(userId, url);
        if (scan) {
            scanFile(file, url);
        }
    }

    @Override
    public File resolveSubmissionFile(Long userId, String url) {
        String localPath = resolveFromUploadPrefix(url);
        if (StrUtil.isBlank(localPath)) {
            localPath = CommonUtil.extractLocalPath(url);
        }
        if (StrUtil.isBlank(localPath) || !WorkProcessingUtil.isPathSafe(localPath)) {
            throw new SecurityException("submission file URL cannot be resolved");
        }

        Path basePath = Paths.get(multiFilePath).toAbsolutePath().normalize();
        Path path = Paths.get(localPath).toAbsolutePath().normalize();
        if (!path.startsWith(basePath)) {
            throw new SecurityException("submission file must come from multi-file upload");
        }
        validateOwner(path, basePath, userId);

        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("submission file does not exist: " + url);
        }
        if (file.length() <= 0) {
            throw new IllegalArgumentException("submission file is empty: " + url);
        }
        return file;
    }

    private String resolveFromUploadPrefix(String url) {
        String localPath = resolveFromPrefix(url, multiFileNginxUrl);
        if (StrUtil.isNotBlank(localPath)) {
            return localPath;
        }
        localPath = resolveFromPrefix(url, uploadNginxUrlPrefix);
        if (StrUtil.isNotBlank(localPath)) {
            return localPath;
        }
        if (url.startsWith("/") && !url.matches("^[A-Za-z]:.*")) {
            return resolveRelativePath(url.substring(1));
        }
        return null;
    }

    private String resolveFromPrefix(String url, String prefix) {
        if (StrUtil.isBlank(url) || StrUtil.isBlank(prefix)) {
            return null;
        }
        String cleanPrefix = trimTrailingSlash(prefix);
        if (!url.startsWith(cleanPrefix + "/")) {
            return null;
        }
        String relative = url.substring(cleanPrefix.length() + 1);
        return resolveRelativePath(relative);
    }

    private String resolveRelativePath(String relative) {
        if (StrUtil.isBlank(relative)) {
            return null;
        }
        String decoded = decodeUrlPath(relative).replace('\\', '/');
        Path basePath = Paths.get(multiFilePath).toAbsolutePath().normalize();
        Path path = basePath.resolve(decoded).normalize();
        if (!path.startsWith(basePath)) {
            throw new SecurityException("submission file path escapes upload directory");
        }
        return path.toString();
    }

    private void validateOwner(Path path, Path basePath, Long userId) {
        Path relative = basePath.relativize(path);
        if (relative.getNameCount() < 4) {
            throw new SecurityException("submission file path is not user-scoped");
        }
        String pathUserId = relative.getName(2).toString();
        if (!String.valueOf(userId).equals(pathUserId)) {
            throw new SecurityException("submission file does not belong to current user");
        }
    }

    private void scanFile(File file, String url) {
        if (!virusScanEnabled) {
            return;
        }
        if (virusScanService == null) {
            throw new IllegalStateException("virus scanner is not available");
        }
        try {
            if (!virusScanService.scanFile(file)) {
                throw new SecurityException("submission file virus scan failed: " + url);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("submission file virus scan failed: " + url, e);
        }
    }

    private LinkedHashSet<String> parseUrls(String fileUrls) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (StrUtil.isBlank(fileUrls)) {
            return urls;
        }
        try {
            JsonNode root = objectMapper.readTree(fileUrls);
            collectUrls(root, urls);
        } catch (Exception e) {
            collectPlainUrls(fileUrls, urls);
        }
        return urls;
    }

    private void collectUrls(JsonNode node, LinkedHashSet<String> urls) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addUrl(node.asText(), urls);
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectUrls(child, urls);
            }
            return;
        }
        if (node.isObject()) {
            addUrl(text(node, "url", "fileUrl", "path"), urls);
            collectUrls(node.get("files"), urls);
            JsonNode categorized = node.get("categorized");
            if (categorized != null && categorized.isObject()) {
                categorized.fields().forEachRemaining(entry -> collectUrls(entry.getValue(), urls));
            }
        }
    }

    private void collectPlainUrls(String value, LinkedHashSet<String> urls) {
        for (String part : value.split("[,\\n]")) {
            addUrl(part, urls);
        }
    }

    private void addUrl(String url, LinkedHashSet<String> urls) {
        if (StrUtil.isNotBlank(url)) {
            urls.add(url.trim());
        }
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child != null && child.isTextual() && StrUtil.isNotBlank(child.asText())) {
                return child.asText();
            }
        }
        return null;
    }

    private String identifyType(String url) {
        return multiFileUploadService.identifyFileType(stripQueryAndFragment(url));
    }

    private String stripQueryAndFragment(String url) {
        String value = url == null ? "" : url;
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        return value;
    }

    private String decodeUrlPath(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            log.warn("event=submission_path_decode_failed");
            return value;
        }
    }

    private String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
