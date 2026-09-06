


package com.haoran.music.common.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;







public final class LyricFileResolver {

    public static final String ORIGINAL_DIRECTORY = "oranial";
    public static final String TRANSLATION_DIRECTORY = "translations";

    private LyricFileResolver() {
    }

    public static List<String> candidatePaths(String songName, String artistNames, Integer lyricType) {
        String safeSongName = sanitizeComponent(songName);
        if (safeSongName == null) {
            return new ArrayList<>();
        }

        Set<String> artists = new LinkedHashSet<>();
        List<String> artistParts = new ArrayList<>();
        String safeArtists = sanitizeComponent(artistNames);
        if (safeArtists != null) {
            artists.add(safeArtists);
            for (String part : artistNames.split("[,，、;；|/]")) {
                String safePart = sanitizeComponent(part);
                if (safePart != null) {
                    artists.add(safePart);
                    artistParts.add(safePart);
                }
            }
            if (artistParts.size() > 1) {
                artists.add(String.join("、", artistParts));
                artists.add(String.join(",", artistParts));
                artists.add(String.join(", ", artistParts));
            }
        }
        if (artists.isEmpty()) {
            artists.add("");
        }

        List<String> directories = isTranslationType(lyricType)
                ? Arrays.asList(TRANSLATION_DIRECTORY, "translation")
                : Arrays.asList(ORIGINAL_DIRECTORY, "original");
        List<String> candidates = new ArrayList<>();
        for (String directory : directories) {
            for (String artist : artists) {
                String filename = safeSongName
                        + (artist.isEmpty() ? "" : "-" + artist)
                        + ".lrc";
                candidates.add(directory + "/" + filename);
            }
        }
        return candidates;
    }

    public static boolean isSafeRelativePath(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("/")
                || path.indexOf('\\') >= 0 || path.indexOf('\u0000') >= 0) {
            return false;
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf('\r') >= 0 || segment.indexOf('\n') >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isTranslationType(Integer lyricType) {
        return lyricType != null && (lyricType == 2 || lyricType == 3);
    }

    private static String sanitizeComponent(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch == '/' || ch == '\\' || ch == '\u0000'
                    || ch == '\r' || ch == '\n' || Character.isISOControl(ch)) {
                result.append('_');
            } else {
                result.append(ch);
            }
        }
        String sanitized = result.toString().trim();
        return sanitized.isEmpty() || ".".equals(sanitized) || "..".equals(sanitized)
                ? null : sanitized;
    }
}
