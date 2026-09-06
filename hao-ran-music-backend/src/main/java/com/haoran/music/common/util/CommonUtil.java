




package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;





public class CommonUtil {

    private static volatile Map<String, String> urlMappings = Collections.emptyMap();




    public static void setUrlMappings(Map<String, String> mappings) {
        if (ObjectUtils.isEmpty(mappings)) {
            urlMappings = Collections.emptyMap();
            return;
        }

        LinkedHashMap<String, String> orderedMappings = new LinkedHashMap<>();
        mappings.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .forEach(entry -> orderedMappings.put(entry.getKey(), entry.getValue()));
        urlMappings = Collections.unmodifiableMap(orderedMappings);
    }






    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }






    public static String getFileExtension(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return "";
        }


        int queryIndex = filePath.indexOf('?');
        if (queryIndex > 0) {
            filePath = filePath.substring(0, queryIndex);
        }

        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1).toLowerCase();
        }

        return "";
    }






    public static String getFileExtensionWithDot(String filePath) {
        String ext = getFileExtension(filePath);
        return ext.isEmpty() ? "" : "." + ext;
    }







    public static Integer extractInt(String text, String regex) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(regex)) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {

        }
        return null;
    }







    public static Double extractDouble(String text, String regex) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(regex)) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {

        }
        return null;
    }







    public static String extractString(String text, String regex) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(regex)) {
            return null;
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {

        }
        return null;
    }






    public static String extractLocalPath(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }

        for (Map.Entry<String, String> entry : urlMappings.entrySet()) {
            String urlPrefix = entry.getKey();
            String localPath = entry.getValue();
            if (StrUtil.isNotBlank(urlPrefix) && StrUtil.isNotBlank(localPath) && url.startsWith(urlPrefix)) {
                return url.replace(urlPrefix, localPath);
            }
        }

        if (url.startsWith("/") || url.matches("^[A-Za-z]:.*")) {
            return url;
        }

        return null;
    }






    public static String getImageFormatName(String filePath) {
        String extension = getFileExtension(filePath);
        if ("jpg".equalsIgnoreCase(extension)) {
            return "jpeg";
        }
        return extension;
    }






    public static String sanitizeFileName(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "file";
        }


        String cleanName = new java.io.File(fileName).getName();


        cleanName = cleanName.replaceAll("[/\\\\:*?\"<>|]", "_");


        if (cleanName.length() > 100) {
            String extension = getFileExtensionWithDot(cleanName);
            String nameWithoutExt = cleanName.substring(0, cleanName.lastIndexOf('.'));
            cleanName = nameWithoutExt.substring(0, 90) + extension;
        }


        if (cleanName.isEmpty() || cleanName.startsWith(".")) {
            cleanName = "file_" + System.currentTimeMillis() + getFileExtensionWithDot(cleanName);
        }

        return cleanName;
    }
}
