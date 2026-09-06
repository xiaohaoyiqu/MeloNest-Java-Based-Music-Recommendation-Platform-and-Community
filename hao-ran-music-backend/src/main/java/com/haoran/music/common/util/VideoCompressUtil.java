   
                      
                                        
   

package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

   
          
  
      
                    
               
                 
            
  
                      
   
@Slf4j
public class VideoCompressUtil {

       
             
       
    public static class CompressResult {
        private final boolean success;
        private final String outputPath;
        private final long originalSize;
        private long compressedSize;
        private final int originalDuration;
        private String thumbnailPath;

        public CompressResult(boolean success, String outputPath, long originalSize, long compressedSize, int originalDuration) {
            this.success = success;
            this.outputPath = outputPath;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.originalDuration = originalDuration;
        }

        public boolean isSuccess() { return success; }
        public String getOutputPath() { return outputPath; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public double getCompressionRatio() {
            return originalSize > 0 ? (1.0 - (double) compressedSize / originalSize) * 100 : 0;
        }
        public int getOriginalDuration() { return originalDuration; }
        public String getThumbnailPath() { return thumbnailPath; }
        public void setThumbnailPath(String path) { this.thumbnailPath = path; }
    }

       
           
       
    public static class VideoInfo {
        private final int duration;
        private final int width;
        private final int height;
        private final long fileSize;
        private final String format;

        public VideoInfo(int duration, int width, int height, long fileSize, String format) {
            this.duration = duration;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
            this.format = format;
        }

        public int getDuration() { return duration; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public long getFileSize() { return fileSize; }
        public String getFormat() { return format; }
    }

       
           
       
    public static class CompressConfig {
        private String videoCodec = "libx264";
        private String preset = "medium";
        private int crf = 26;
        private int bitrate = 0;
        private String audioCodec = "aac";
        private int audioBitrate = 128000;
        private int audioChannels = 2;
        private int maxDuration = 300;
        private int minDuration = 3;
        private long maxSize = 209715200;
        private boolean generateThumbnail = true;
        private int thumbnailTime = 5;

        public CompressConfig() {}

        public CompressConfig setVideoCodec(String videoCodec) {
            this.videoCodec = videoCodec;
            return this;
        }

        public CompressConfig setPreset(String preset) {
            this.preset = preset;
            return this;
        }

        public CompressConfig setCrf(int crf) {
            this.crf = crf;
            return this;
        }

        public CompressConfig setBitrate(int bitrate) {
            this.bitrate = bitrate;
            return this;
        }

        public CompressConfig setMaxDuration(int maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public CompressConfig setMinDuration(int minDuration) {
            this.minDuration = minDuration;
            return this;
        }

        public CompressConfig setMaxSize(long maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public CompressConfig setGenerateThumbnail(boolean generateThumbnail) {
            this.generateThumbnail = generateThumbnail;
            return this;
        }

        public CompressConfig setThumbnailTime(int thumbnailTime) {
            this.thumbnailTime = thumbnailTime;
            return this;
        }

        public String getVideoCodec() { return videoCodec; }
        public String getPreset() { return preset; }
        public int getCrf() { return crf; }
        public int getBitrate() { return bitrate; }
        public String getAudioCodec() { return audioCodec; }
        public int getAudioBitrate() { return audioBitrate; }
        public int getAudioChannels() { return audioChannels; }
        public int getMaxDuration() { return maxDuration; }
        public int getMinDuration() { return minDuration; }
        public long getMaxSize() { return maxSize; }
        public boolean isGenerateThumbnail() { return generateThumbnail; }
        public int getThumbnailTime() { return thumbnailTime; }
    }

       
                    
       
    private static final CompressConfig DEFAULT_CONFIG = new CompressConfig()
            .setPreset("medium")
            .setCrf(26)
            .setBitrate(0);

       
                   
       
    public static boolean isFFmpegAvailable() {
        try {
            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(
                    Arrays.asList(MediaToolPathResolver.ffmpeg(), "-version"), 10);
            return !result.isTimedOut() && result.getExitCode() == 0;
        } catch (IOException | InterruptedException e) {
            log.error("event=ffmpeg_availability_check_failed errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }

       
             
       
    public static VideoInfo getVideoInfo(String inputPath) {
        if (!isFFmpegAvailable()) {
            log.error("event=video_probe_rejected reason=ffmpeg_unavailable");
            return null;
        }

                         
        if (!WorkProcessingUtil.isPathSafe(inputPath)) {
            log.warn("event=video_probe_rejected reason=unsafe_path");
            return null;
        }

        try {
            ProcessExecutionUtil.Result processResult = ProcessExecutionUtil.execute(Arrays.asList(
                    MediaToolPathResolver.ffprobe(), "-v", "quiet",
                    "-show_streams", "-show_format", "-of", "json", inputPath), 30);
            if (processResult.isTimedOut() || processResult.getExitCode() != 0) {
                return null;
            }
            String result = processResult.getOutput();

            int duration = parseDuration(result);
            int width = parseWidth(result);
            int height = parseHeight(result);
            long fileSize = new File(inputPath).length();
            String format = parseFormat(result);

            return new VideoInfo(duration, width, height, fileSize, format);

        } catch (IOException | InterruptedException e) {
            log.error("event=video_probe_failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

       
           
       
    public static CompressResult compress(String inputPath, String outputPath) {
        return compress(inputPath, outputPath, DEFAULT_CONFIG);
    }

       
                 
       
    public static CompressResult compress(String inputPath, String outputPath, CompressConfig config) {
                         
        if (!WorkProcessingUtil.isPathSafe(inputPath) || !WorkProcessingUtil.isPathSafe(outputPath)) {
            log.warn("event=video_compress_rejected reason=unsafe_path");
            return new CompressResult(false, outputPath, 0, 0, 0);
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            log.warn("event=video_compress_rejected reason=input_missing");
            return new CompressResult(false, outputPath, 0, 0, 0);
        }

        long originalSize = inputFile.length();

        if (originalSize > config.getMaxSize()) {
            log.warn("event=video_compress_rejected reason=file_too_large originalBytes={} maxBytes={}",
                originalSize, config.getMaxSize());
            return new CompressResult(false, outputPath, originalSize, 0, 0);
        }

        VideoInfo info = getVideoInfo(inputPath);
        if (info != null && (info.getDuration() < config.getMinDuration()
                || info.getDuration() > config.getMaxDuration())) {
            log.warn("event=video_compress_rejected reason=duration_out_of_range durationSeconds={} minSeconds={} maxSeconds={}",
                info.getDuration(), config.getMinDuration(), config.getMaxDuration());
            return new CompressResult(false, outputPath, originalSize, 0, 0);
        }

        try {
            Files.createDirectories(Paths.get(outputPath).getParent());

            List<String> commands = new ArrayList<>();
            commands.add(MediaToolPathResolver.ffmpeg());
            commands.add("-i");
            commands.add(inputPath);

            commands.add("-c:v");
            commands.add(config.getVideoCodec());
            commands.add("-preset");
            commands.add(config.getPreset());
            commands.add("-crf");
            commands.add(String.valueOf(config.getCrf()));

            if (config.getBitrate() > 0) {
                commands.add("-b:v");
                commands.add(String.valueOf(config.getBitrate()));
            }

            commands.add("-c:a");
            commands.add(config.getAudioCodec());
            commands.add("-b:a");
            commands.add(String.valueOf(config.getAudioBitrate()));
            commands.add("-ac");
            commands.add(String.valueOf(config.getAudioChannels()));

            commands.add("-movflags");
            commands.add("+faststart");

            commands.add(outputPath);

            long timeoutSeconds = Math.max(60L, config.getMaxDuration() * 12L);
            ProcessExecutionUtil.Result processResult =
                    ProcessExecutionUtil.execute(commands, timeoutSeconds);
            int exitCode = processResult.isTimedOut() ? -1 : processResult.getExitCode();
            if (processResult.isTimedOut()) {
                log.warn("event=video_compress_timeout timeoutSeconds={}", timeoutSeconds);
            }

            File outputFile = new File(outputPath);
            long compressedSize = outputFile.length();
            int duration = info != null ? info.getDuration() : 0;

            CompressResult result = new CompressResult(
                exitCode == 0 && outputFile.exists(),
                outputPath,
                originalSize,
                compressedSize,
                duration
            );

            if (result.isSuccess() && config.isGenerateThumbnail()) {
                String thumbnailPath = generateThumbnail(
                    inputPath,
                    outputPath.replace(".mp4", "_cover.jpg"),
                    config.getThumbnailTime()
                );
                result.setThumbnailPath(thumbnailPath);
            }

            log.info("event=video_compress_succeeded originalBytes={} compressedBytes={} compressionPercent={}",
                    originalSize, compressedSize, result.getCompressionRatio());

            return result;

        } catch (IOException | InterruptedException e) {
            log.error("event=video_compress_failed errorType={}", e.getClass().getSimpleName());
            return new CompressResult(false, outputPath, originalSize, 0, 0);
        }
    }

       
             
       
    public static String generateThumbnail(String videoPath, String outputPath, int timeSeconds) {
        try {
            List<String> commands = new ArrayList<>();
            commands.add(MediaToolPathResolver.ffmpeg());
            commands.add("-i");
            commands.add(videoPath);
            commands.add("-ss");
            commands.add(String.valueOf(timeSeconds));
            commands.add("-vframes");
            commands.add("1");
            commands.add("-q:v");
            commands.add("2");
            commands.add("-y");
            commands.add(outputPath);

            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(commands, 120);
            int exitCode = result.isTimedOut() ? -1 : result.getExitCode();

            if (exitCode == 0 && new File(outputPath).exists()) {
                log.info("event=video_cover_generation_succeeded");
                return outputPath;
            }

        } catch (IOException | InterruptedException e) {
            log.error("event=video_cover_generation_failed errorType={}", e.getClass().getSimpleName());
        }
        return null;
    }

                                                     

    private static int parseDuration(String ffprobeOutput) {
        int duration = 0;
        try {
            int durationIndex = ffprobeOutput.indexOf("\"duration\":");
            if (durationIndex > 0) {
                int startIndex = durationIndex + "\"duration\":\"".length();
                int endIndex = ffprobeOutput.indexOf(",", startIndex);
                if (endIndex > startIndex) {
                    String durationStr = ffprobeOutput.substring(startIndex, endIndex);
                    duration = (int) Double.parseDouble(durationStr);
                }
            }
        } catch (Exception e) {
                   
        }
        return duration;
    }

    private static int parseWidth(String ffprobeOutput) {
        try {
            int widthIndex = ffprobeOutput.indexOf("\"width\":");
            if (widthIndex > 0) {
                int startIndex = widthIndex + "\"width\":".length();
                int endIndex = ffprobeOutput.indexOf(",", startIndex);
                if (endIndex > startIndex) {
                    return Integer.parseInt(ffprobeOutput.substring(startIndex, endIndex));
                }
            }
        } catch (Exception e) {
                         
        }
        return 1280;
    }

    private static int parseHeight(String ffprobeOutput) {
        try {
            int heightIndex = ffprobeOutput.indexOf("\"height\":");
            if (heightIndex > 0) {
                int startIndex = heightIndex + "\"height\":".length();
                int endIndex = ffprobeOutput.indexOf(",", startIndex);
                if (endIndex > startIndex) {
                    return Integer.parseInt(ffprobeOutput.substring(startIndex, endIndex));
                }
            }
        } catch (Exception e) {
                         
        }
        return 720;
    }

    private static String parseFormat(String ffprobeOutput) {
        try {
            int formatIndex = ffprobeOutput.indexOf("\"format_name\":");
            if (formatIndex > 0) {
                int startIndex = formatIndex + "\"format_name\":\"".length();
                int endIndex = ffprobeOutput.indexOf("\"", startIndex);
                if (endIndex > startIndex) {
                    return ffprobeOutput.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
                          
        }
        return "mp4";
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f MB", bytes / 1024.0);
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0));
        }
    }
}
