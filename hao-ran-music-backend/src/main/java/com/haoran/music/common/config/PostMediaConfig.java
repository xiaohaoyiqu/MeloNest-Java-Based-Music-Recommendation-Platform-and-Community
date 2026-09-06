   
                      
                                                               
   
package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
                                                                       
   
@Data
@Component
@ConfigurationProperties(prefix = "music.post")
public class PostMediaConfig {

    private String imageOriginalPath;
    private String imageCompressedPath;
    private String imageThumbnailPath;
    private String nginxUrlPrefix;
    private Float imageCompressedQuality = 0.85f;
    private Float imageThumbnailQuality = 0.80f;
    private Long imageMaxFileSize = 10L * 1024 * 1024;
    private Integer imageMaxFiles = 9;
    private List<String> imageAllowedExtensions = new ArrayList<>(Arrays.asList(
            ".jpeg", ".jpg", ".png", ".webp"
    ));

    private String videoOriginalPath;
    private String videoCachePath;
    private String video480pPath;
    private String video720pPath;
    private String videoThumbnailPath;
    private String videoTempPath;
    private String videoNginxUrlPrefix;
    private Long videoMaxSize = 524288000L;
    private Integer videoMaxDuration = 300;
    private Integer videoMinDuration = 3;
    private Integer videoCacheDays = 7;
    private Integer videoCrf720p = 24;
    private Integer videoCrf480p = 26;
    private Integer videoThumbnailTime = 5;
    private String videoFfmpegPath = "/usr/local/soft/ffmpeg-6.1.1/bin/ffmpeg";
    private String videoFfprobePath = "/usr/local/soft/ffmpeg-6.1.1/bin/ffprobe";
    private Integer videoProbeTimeoutSeconds = 30;
    private Integer videoTranscodeTimeoutSeconds = 600;
    private Integer videoSshTimeoutSeconds = 60;
    private List<String> videoAllowedContentTypes = new ArrayList<>(Arrays.asList(
            "video/mp4", "video/quicktime", "video/x-msvideo"
    ));
}


