package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.SongArtistMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.service.SongCreditProjectionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;





@Slf4j
@Component
public class WorkProcessingUtil {

    @Autowired
    private ArtistMapper artistMapper;

    @Autowired
    private AlbumMapper albumMapper;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private SongArtistMapper songArtistMapper;

    @Autowired
    private SongCreditProjectionService songCreditProjectionService;




    private static final Map<Integer, String> QUALITY_FIELD_MAP;
    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "urlStandard");
        map.put(2, "urlHigh");
        map.put(3, "urlLossless");
        map.put(4, "urlHires");
        map.put(5, "urlMaster");
        QUALITY_FIELD_MAP = Collections.unmodifiableMap(map);
    }




    private static final Map<Integer, String> SIZE_FIELD_MAP;
    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "sizeStandard");
        map.put(2, "sizeHigh");
        map.put(3, "sizeLossless");
        map.put(4, "sizeHires");
        map.put(5, "sizeMaster");
        SIZE_FIELD_MAP = Collections.unmodifiableMap(map);
    }




    private static final Map<String, String> VIDEO_QUALITY_FIELD_MAP;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("360p", "url360p");
        map.put("480p", "url360p");
        map.put("720p", "url720p");
        map.put("hd", "url720p");
        map.put("1080p", "url1080p");
        map.put("fhd", "url1080p");
        map.put("4k", "url2160p");
        map.put("2160p", "url2160p");
        VIDEO_QUALITY_FIELD_MAP = Collections.unmodifiableMap(map);
    }




    public static class AudioDetectionResult {
        private Integer qualityLevel;
        private String qualityName;
        private Long fileSize;
        private Integer duration;
        private Integer bitrate;
        private Integer sampleRate;
        private String format;

        public Integer getQualityLevel() { return qualityLevel; }
        public void setQualityLevel(Integer qualityLevel) { this.qualityLevel = qualityLevel; }
        public String getQualityName() { return qualityName; }
        public void setQualityName(String qualityName) { this.qualityName = qualityName; }
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public Integer getBitrate() { return bitrate; }
        public void setBitrate(Integer bitrate) { this.bitrate = bitrate; }
        public Integer getSampleRate() { return sampleRate; }
        public void setSampleRate(Integer sampleRate) { this.sampleRate = sampleRate; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }




    public static class VideoDetectionResult {
        private Long fileSize;
        private Integer duration;
        private String quality;
        private String format;
        private Integer width;
        private Integer height;

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        public Integer getDuration() { return duration; }
        public void setDuration(Integer duration) { this.duration = duration; }
        public String getQuality() { return quality; }
        public void setQuality(String quality) { this.quality = quality; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public Integer getWidth() { return width; }
        public void setWidth(Integer width) { this.width = width; }
        public Integer getHeight() { return height; }
        public void setHeight(Integer height) { this.height = height; }
    }




    public static VideoDetectionResult detectVideoInfo(String localFilePath) {
        VideoDetectionResult info = new VideoDetectionResult();

        if (StrUtil.isBlank(localFilePath)) {
            return info;
        }

        if (!isPathSafe(localFilePath)) {
            log.error("event=work_video_detection_rejected reason=PATH_INVALID");
            return info;
        }

        File file = new File(localFilePath);
        if (!file.exists()) {
            log.warn("event=work_video_detection_rejected reason=FILE_MISSING");
            return info;
        }

        info.setFileSize(file.length());

        try {
            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(Arrays.asList(
                MediaToolPathResolver.ffprobe(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                localFilePath
            ), 30);

            if (result.isTimedOut()) {
                return info;
            }

            if (result.getExitCode() == 0) {
                parseVideoInfo(result.getOutput(), info);
            }

        } catch (Exception e) {
            log.warn("event=work_video_detection_failed errorType={}",
                    e.getClass().getSimpleName());
        }

        return info;
    }




    private static void parseVideoInfo(String jsonOutput, VideoDetectionResult info) {
        Double duration = CommonUtil.extractDouble(jsonOutput, "\"duration\"\\s*:\\s*(\\d+\\.?\\d*)");
        if (duration != null) {
            info.setDuration(duration.intValue());
        }

        Integer width = CommonUtil.extractInt(jsonOutput, "\"width\"\\s*:\\s*(\\d+)");
        Integer height = CommonUtil.extractInt(jsonOutput, "\"height\"\\s*:\\s*(\\d+)");

        if (width != null && height != null) {
            info.setWidth(width);
            info.setHeight(height);
            info.setQuality(determineVideoQuality(width, height));
        }

        String codec = CommonUtil.extractString(jsonOutput, "\"codec_name\"\\s*:\\s*\"([^\"]+)\"");
        if (codec != null) {
            info.setFormat(codec);
        }
    }




    private static String determineVideoQuality(Integer width, Integer height) {
        int minDim = Math.min(width, height);
        if (minDim >= 2160) return "4k";
        if (minDim >= 1080) return "1080p";
        if (minDim >= 720) return "720p";
        if (minDim >= 480) return "480p";
        return "360p";
    }




    public static String getQualityUrlField(Integer qualityLevel) {
        return QUALITY_FIELD_MAP.getOrDefault(qualityLevel, "urlHigh");
    }




    public static String getQualitySizeField(Integer qualityLevel) {
        return SIZE_FIELD_MAP.getOrDefault(qualityLevel, "sizeHigh");
    }




    public static String getVideoQualityField(String quality) {
        return VIDEO_QUALITY_FIELD_MAP.getOrDefault(quality.toLowerCase(), "url720p");
    }




    public static void setSongQualityField(Song song, Integer qualityLevel, String url, Long size) {
        String urlField = getQualityUrlField(qualityLevel);
        String sizeField = getQualitySizeField(qualityLevel);

        setSongField(song, urlField, url);
        setSongField(song, sizeField, size != null ? String.valueOf(size) : "0");
    }




    private static void setSongField(Song song, String fieldName, String value) {
        try {
            Field field = song.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(song, value);
        } catch (Exception e) {
            log.warn("event=work_field_assignment_failed fieldName={} errorType={}",
                    fieldName, e.getClass().getSimpleName());
        }
    }




    public Artist getOrCreateArtist(String artistName) {
        if (StrUtil.isBlank(artistName)) {
            artistName = "未知歌手";
        }

        LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<Artist>()
            .eq(Artist::getName, artistName);
        Artist artist = artistMapper.selectOne(wrapper);

        if (artist == null) {
            artist = new Artist();
            artist.setName(artistName);
            artist.setCover("/default-artist.png");
            artist.setDescription("投稿自动创建");
            artist.setCreateTime(LocalDateTime.now());
            artist.setUpdateTime(LocalDateTime.now());
            artistMapper.insert(artist);
            log.info("event=artist_profile_created artistId={}", artist.getId());
        }

        return artist;
    }




    public void createSongArtistRelation(Long songId, Long artistId, String artistName) {
        com.haoran.music.entity.SongArtist songArtist = new com.haoran.music.entity.SongArtist();
        songArtist.setSongId(songId);
        songArtist.setArtistId(artistId);
        songArtist.setArtistName(artistName);
        songArtist.setType(1);
        songArtist.setSortOrder(1);
        songArtist.setCreateTime(LocalDateTime.now());
        if (songArtistMapper.insert(songArtist) != 1) {
            throw new IllegalStateException("song artist relation insert conflict");
        }
        songCreditProjectionService.syncDisplayCredits(songId, Collections.singletonList(songArtist),
                "work_processing", "published work artist relation sync");
    }




    public static String extractLocalPath(String url) {
        return CommonUtil.extractLocalPath(url);
    }




    public static boolean isPathSafe(String path) {
        if (StrUtil.isBlank(path)) {
            return false;
        }
        if (path.contains("|") || path.contains("&") || path.contains(";") ||
            path.contains("$") || path.contains("`") || path.contains("\n") ||
            path.contains("\r")) {
            return false;
        }
        if (path.contains("..")) {
            return false;
        }
        return true;
    }




    public static String validateAndCleanPath(String path) {
        if (!isPathSafe(path)) {
            throw new SecurityException("路径包含非法字符或不安全: " + path);
        }
        return path.replaceAll("/+", "/");
    }
}
