package com.haoran.music.common.util;

import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.constant.AudioQualityConstants;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.enums.AudioQuality;
import com.haoran.music.enums.SoundQuality;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;





@Slf4j
@Component
public class AudioQualityDetector {




    @Value("${audio.ffmpeg.path}")
    private String ffprobePath;




    @Value("${audio.ffmpeg.enabled}")
    private boolean ffmpegEnabled;




    @Value("${audio.ffmpeg.detect-timeout}")
    private int detectTimeout;

    @Value("${audio.ffmpeg.version-check-timeout-seconds}")
    private int versionCheckTimeoutSeconds;




    @Data
    public static class AudioInfo {
        private Integer qualityLevel;                   
        private String qualityName;               
        private Long fileSize;                        
        private Integer duration;                  
        private Integer bitrate;                       
        private Integer sampleRate;                  
        private Integer bitDepth;                     
        private String format;                    
        private String codec;                     
        private Integer channels;                
        private boolean detectedByFFmpeg;                  

        public AudioInfo() {
            this.qualityLevel = AudioQualityConstants.QUALITY_STANDARD;
            this.qualityName = AudioQualityConstants.NAME_STANDARD;
            this.detectedByFFmpeg = false;
        }


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
        public Integer getBitDepth() { return bitDepth; }
        public void setBitDepth(Integer bitDepth) { this.bitDepth = bitDepth; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getCodec() { return codec; }
        public void setCodec(String codec) { this.codec = codec; }
        public Integer getChannels() { return channels; }
        public void setChannels(Integer channels) { this.channels = channels; }
        public boolean isDetectedByFFmpeg() { return detectedByFFmpeg; }
        public void setDetectedByFFmpeg(boolean detectedByFFmpeg) { this.detectedByFFmpeg = detectedByFFmpeg; }
    }






    public Integer detectQuality(String filePath) {
        AudioInfo info = detectAudioInfo(filePath);
        return info.getQualityLevel();
    }






    public AudioInfo detectAudioInfo(String filePath) {
        AudioInfo info = new AudioInfo();

        if (StrUtil.isBlank(filePath)) {
            log.warn("event=audio_quality_detection_skipped reason=PATH_MISSING");
            return info;
        }


        String extension = CommonUtil.getFileExtension(filePath);
        info.setFormat(extension);


        String localPath = CommonUtil.extractLocalPath(filePath);

        File file = null;
        if (localPath != null) {
            file = new File(localPath);
        }


        if (file != null && file.exists() && ffmpegEnabled) {
            try {
                AudioInfo ffmpegInfo = detectWithFFmpeg(file.getAbsolutePath());
                if (ffmpegInfo != null) {
                    log.info("event=audio_quality_detection_completed detector=ffmpeg qualityLevel={}",
                            ffmpegInfo.getQualityLevel());
                    return ffmpegInfo;
                }
            } catch (Exception e) {
                log.warn("event=audio_quality_detection_fallback errorType={}",
                        e.getClass().getSimpleName());
            }
        }


        return detectByFormat(extension, info);
    }






    public AudioInfo detectWithFFmpeg(String localFilePath) {

        if (!WorkProcessingUtil.isPathSafe(localFilePath)) {
            log.error("event=audio_quality_detection_rejected reason=PATH_INVALID");
            return null;
        }

        File file = new File(localFilePath);
        if (!file.exists()) {
            log.warn("event=audio_quality_detection_rejected reason=FILE_MISSING");
            return null;
        }

        AudioInfo info = new AudioInfo();
        info.setFileSize(file.length());
        info.setDetectedByFFmpeg(true);

        try {
            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(Arrays.asList(
                ffprobePath,
                "-v", "quiet",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                localFilePath
            ), Math.max(1, detectTimeout / 1000));

            if (result.isTimedOut()) {
                log.warn("event=audio_quality_detection_timeout");
                return null;
            }

            if (result.getExitCode() != 0) {
                log.warn("event=audio_quality_detection_process_failed exitCode={}", result.getExitCode());
                return null;
            }


            return parseFFmpegOutput(result.getOutput(), info);

        } catch (Exception e) {
            log.error("event=audio_quality_detection_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }




    private AudioInfo parseFFmpegOutput(String jsonOutput, AudioInfo info) {
        try {

            Integer bitrate = CommonUtil.extractInt(jsonOutput, "\"bit_rate\"\\s*:\\s*\"?(\\d+)");
            if (bitrate != null && bitrate > AudioQualityConstants.BITRATE_CONVERT_FACTOR) {
                info.setBitrate(bitrate / AudioQualityConstants.BITRATE_CONVERT_FACTOR);           
            }


            Integer sampleRate = CommonUtil.extractInt(jsonOutput, "\"sample_rate\"\\s*:\\s*\"?(\\d+)");
            if (sampleRate != null) {
                info.setSampleRate(sampleRate);
            }


            Integer bitDepth = CommonUtil.extractInt(jsonOutput, "\"bits_per_sample\"\\s*:\\s*\"?(\\d+)");
            if (bitDepth != null) {
                info.setBitDepth(bitDepth);
            }


            Double duration = CommonUtil.extractDouble(jsonOutput, "\"duration\"\\s*:\\s*(\\d+\\.?\\d*)");
            if (duration != null) {
                info.setDuration(duration.intValue());
            }


            String codec = CommonUtil.extractString(jsonOutput, "\"codec_name\"\\s*:\\s*\"([^\"]+)\"");
            if (codec != null) {
                info.setCodec(codec);
            }


            String format = CommonUtil.extractString(jsonOutput, "\"format_name\"\\s*:\\s*\"([^\"]+)\"");
            if (format != null) {
                info.setFormat(format.split(",")[0]);          
            }


            Integer channels = CommonUtil.extractInt(jsonOutput, "\"channels\"\\s*:\\s*\"?(\\d+)");
            if (channels != null) {
                info.setChannels(channels);
            }


            determineQualityLevel(info);

            return info;

        } catch (Exception e) {
            log.error("event=audio_quality_output_parse_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }




    private void determineQualityLevel(AudioInfo info) {
        int sampleRate = info.getSampleRate() != null ? info.getSampleRate() : 0;
        int bitDepth = info.getBitDepth() != null ? info.getBitDepth() : 16;
        int bitrate = info.getBitrate() != null ? info.getBitrate() : 0;
        String codec = info.getCodec() != null ? info.getCodec().toLowerCase() : "";
        String format = info.getFormat() != null ? info.getFormat().toLowerCase() : "";


        boolean isLosslessCodec = AudioQualityConstants.isLosslessCodec(codec);


        if (sampleRate >= AudioQualityConstants.MASTER_MIN_SAMPLE_RATE && bitDepth >= AudioQualityConstants.MASTER_MIN_BIT_DEPTH) {
            info.setQualityLevel(AudioQualityConstants.QUALITY_MASTER);
            info.setQualityName(AudioQualityConstants.NAME_MASTER);
        }

        else if (sampleRate >= AudioQualityConstants.HIRES_MIN_SAMPLE_RATE && bitDepth >= AudioQualityConstants.HIRES_MIN_BIT_DEPTH) {
            info.setQualityLevel(AudioQualityConstants.QUALITY_HIRES);
            info.setQualityName(AudioQualityConstants.NAME_HIRES);
        }

        else if (isLosslessCodec || format.contains(AudioQualityConstants.FORMAT_FLAC) ||
                 (format.contains(AudioQualityConstants.FORMAT_WAV) && bitDepth >= 16)) {
            info.setQualityLevel(AudioQualityConstants.QUALITY_LOSSLESS);
            info.setQualityName(AudioQualityConstants.NAME_LOSSLESS);
        }

        else if (bitrate >= AudioQualityConstants.HIGH_MIN_BITRATE) {
            info.setQualityLevel(AudioQualityConstants.QUALITY_HIGH);
            info.setQualityName(AudioQualityConstants.NAME_HIGH);
        }

        else {
            info.setQualityLevel(AudioQualityConstants.QUALITY_STANDARD);
            info.setQualityName(AudioQualityConstants.NAME_STANDARD);
        }
    }




    private AudioInfo detectByFormat(String extension, AudioInfo info) {
        info.setDetectedByFFmpeg(false);

        if ("flac".equals(extension) || "wav".equals(extension)) {

            info.setQualityLevel(AudioQualityConstants.QUALITY_LOSSLESS);
            info.setQualityName("无损音质");
        } else if ("ape".equals(extension) || "wv".equals(extension)) {

            info.setQualityLevel(AudioQualityConstants.QUALITY_LOSSLESS);
            info.setQualityName("无损音质");
        } else if ("mp3".equals(extension)) {

            info.setQualityLevel(AudioQualityConstants.QUALITY_HIGH);
            info.setQualityName("高品质音质");
        } else if ("m4a".equals(extension) || "aac".equals(extension) ||
                   "ogg".equals(extension) || "opus".equals(extension)) {

            info.setQualityLevel(AudioQualityConstants.QUALITY_HIGH);
            info.setQualityName("高品质音质");
        } else {

            info.setQualityLevel(AudioQualityConstants.QUALITY_STANDARD);
            info.setQualityName("标准音质");
        }

        return info;
    }




    public String getQualityName(Integer qualityCode) {
        switch (qualityCode) {
            case AudioQualityConstants.QUALITY_MASTER:
                return "母带音质";
            case AudioQualityConstants.QUALITY_HIRES:
                return "Hi-Res音质";
            case AudioQualityConstants.QUALITY_LOSSLESS:
                return "无损音质";
            case AudioQualityConstants.QUALITY_HIGH:
                return "高品质音质";
            case AudioQualityConstants.QUALITY_STANDARD:
            default:
                return "标准音质";
        }
    }




    public SoundQuality getSoundQuality(Integer qualityCode) {
        switch (qualityCode) {
            case AudioQualityConstants.QUALITY_MASTER:
                return SoundQuality.MASTER;
            case AudioQualityConstants.QUALITY_HIRES:
                return SoundQuality.HIRES;
            case AudioQualityConstants.QUALITY_LOSSLESS:
                return SoundQuality.LOSSLESS;
            case AudioQualityConstants.QUALITY_HIGH:
                return SoundQuality.HIGH;
            case AudioQualityConstants.QUALITY_STANDARD:
            default:
                return SoundQuality.STANDARD;
        }
    }







    public AudioInfo detectFromUrl(String fileUrl, String localDownloadPath) {
        String localPath = CommonUtil.extractLocalPath(fileUrl);
        if (localPath != null) {
            File file = new File(localPath);
            if (file.exists()) {
                return detectWithFFmpeg(localPath);
            }
        }


        return detectAudioInfo(fileUrl);
    }






    public java.util.List<AudioInfo> batchDetect(java.util.List<String> filePaths) {
        java.util.List<AudioInfo> results = new java.util.ArrayList<>();
        for (String path : filePaths) {
            try {
                AudioInfo info = detectAudioInfo(path);
                results.add(info);
            } catch (Exception e) {
                log.error("event=audio_quality_batch_item_failed errorType={}",
                        e.getClass().getSimpleName());
                AudioInfo errorInfo = new AudioInfo();
                errorInfo.setFormat(CommonUtil.getFileExtension(path));
                results.add(errorInfo);
            }
        }
        return results;
    }




    public boolean isFFmpegAvailable() {
        try {
            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(
                    Arrays.asList(ffprobePath, "-version"),
                    Math.max(1, versionCheckTimeoutSeconds));
            return !result.isTimedOut() && result.getExitCode() == 0;
        } catch (Exception e) {
            log.warn("event=audio_quality_detector_unavailable errorType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
