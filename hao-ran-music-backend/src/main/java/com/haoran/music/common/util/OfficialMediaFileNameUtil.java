package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

   
                
  
                      
   
public final class OfficialMediaFileNameUtil {

    private static final int MAX_FILE_NAME_BYTES = 240;
    private static final String ILLEGAL_FILE_CHARS = "[\\x00-\\x1f\\x7f<>:\"/\\\\|?*]+";

    private OfficialMediaFileNameUtil() {
    }

       
                                     
                               
      
                          
                                               
                             
                               
                                    
       
    public static String build(String songName, String artistNames, String versionName, String extension) {
        String title = sanitizeSegment(songName, "未命名歌曲");
        String version = sanitizeSegment(versionName, "");
        if (StrUtil.isNotBlank(version)
                && !title.toLowerCase(Locale.ROOT).contains(version.toLowerCase(Locale.ROOT))) {
            title = title + " (" + version + ")";
        }
        String artists = normalizeArtists(artistNames);
        String safeExtension = normalizeExtension(extension);
        String suffix = "." + safeExtension;
        String baseName = title + " - " + artists;
        return truncateUtf8(baseName, MAX_FILE_NAME_BYTES - utf8Length(suffix)) + suffix;
    }

    private static String normalizeArtists(String artistNames) {
        if (StrUtil.isBlank(artistNames)) {
            return "未知歌手";
        }
        Set<String> artists = new LinkedHashSet<>();
        for (String artist : artistNames.split("[,，、;；]+")) {
            String normalized = sanitizeSegment(artist, "");
            if (StrUtil.isNotBlank(normalized)) {
                artists.add(normalized);
            }
        }
        return artists.isEmpty() ? "未知歌手" : String.join(", ", artists);
    }

    private static String normalizeExtension(String extension) {
        String normalized = StrUtil.blankToDefault(extension, "bin").trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceFirst("^\\.+", "").replaceAll("[^a-z0-9]", "");
        return StrUtil.isBlank(normalized) ? "bin" : normalized.substring(0, Math.min(10, normalized.length()));
    }

    private static String sanitizeSegment(String value, String fallback) {
        if (StrUtil.isBlank(value)) {
            return fallback;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .replaceAll(ILLEGAL_FILE_CHARS, " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^[. ]+|[. ]+$", "")
                .trim();
        return StrUtil.isBlank(normalized) ? fallback : normalized;
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (utf8Length(value) <= maxBytes) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int bytes = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = utf8Length(character);
            if (bytes + characterBytes > maxBytes) {
                break;
            }
            result.append(character);
            bytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return result.toString().replaceAll("[. ]+$", "");
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
