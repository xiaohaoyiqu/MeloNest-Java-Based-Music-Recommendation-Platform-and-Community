   
                      
                                         
  
           
                                           
                                  
                                   
                         
  
        
                                                            
                                
                                           
   

package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

   
          
  
        
        
          
                                                                                                          
  
          
                                                                                                   
  
          
                                                                                      
         
   
@Slf4j
public class CompressUtil {

                                                     

       
                        
       
    private static final float DEFAULT_IMAGE_QUALITY = 0.85f;

       
                             
       
    private static final int DEFAULT_VIDEO_CRF = 26;

       
                    
       
    private static final int DEFAULT_AUDIO_BITRATE = 128;

       
              
       
    private static final Set<String> IMAGE_FORMATS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    ));

       
              
       
    private static final Set<String> VIDEO_FORMATS = new HashSet<>(Arrays.asList(
            "mp4", "mov", "avi", "mkv", "flv", "wmv"
    ));

       
              
       
    private static final Set<String> AUDIO_FORMATS = new HashSet<>(Arrays.asList(
            "mp3", "aac", "flac", "wav", "ogg", "m4a"
    ));

                                                      

       
             
       
    public static class CompressResult {
        private final boolean success;
        private final String inputPath;
        private final String outputPath;
        private final long originalSize;
        private final long compressedSize;
        private final String type;
        private final String message;
        private final long compressTime;

        public CompressResult(boolean success, String inputPath, String outputPath,
                             long originalSize, long compressedSize, String type, String message, long compressTime) {
            this.success = success;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.type = type;
            this.message = message;
            this.compressTime = compressTime;
        }

        public boolean isSuccess() { return success; }
        public String getInputPath() { return inputPath; }
        public String getOutputPath() { return outputPath; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public double getCompressionRatio() {
            return originalSize > 0 ? (1.0 - (double) compressedSize / originalSize) * 100 : 0;
        }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public long getCompressTime() { return compressTime; }

        @Override
        public String toString() {
            return String.format("CompressResult{success=%s, type=%s, original=%s, compressed=%s, ratio=%.2f%%, time=%dms}",
                    success, type, formatSize(originalSize), formatSize(compressedSize), getCompressionRatio(), compressTime);
        }
    }

                                                     

       
                   
      
                             
                             
                   
       
    public static CompressResult compressImage(String inputPath, String outputPath) {
        return compressImage(inputPath, outputPath, DEFAULT_IMAGE_QUALITY);
    }

       
                 
      
                             
                             
                                      
                   
       
    public static CompressResult compressImage(String inputPath, String outputPath, float quality) {
        long startTime = System.currentTimeMillis();
        File inputFile = new File(inputPath);

        if (!inputFile.exists()) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "image",
                    "输入文件不存在", System.currentTimeMillis() - startTime);
        }

        long originalSize = inputFile.length();

        try {
                        
            EnhancedImageCompressUtil.CompressResult result =
                    EnhancedImageCompressUtil.compress(inputPath, outputPath, quality);

            long compressTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                return new CompressResult(true, inputPath, outputPath,
                        originalSize, result.getCompressedSize(), "image",
                        "图片压缩成功", compressTime);
            } else {
                return new CompressResult(false, inputPath, outputPath,
                        originalSize, 0, "image",
                        "图片压缩失败", compressTime);
            }

        } catch (Exception e) {
            log.error("图片压缩异常: {}", e.getClass().getSimpleName());
            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "image",
                    "压缩异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

       
                     
      
                             
                                    
                                      
                   
       
    public static CompressResult convertToWebP(String inputPath, String outputPath, float quality) {
        long startTime = System.currentTimeMillis();
        File inputFile = new File(inputPath);

        if (!inputFile.exists()) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "webp",
                    "输入文件不存在", System.currentTimeMillis() - startTime);
        }

        long originalSize = inputFile.length();

        try {
            EnhancedImageCompressUtil.CompressResult result =
                    EnhancedImageCompressUtil.convertToWebP(inputPath, outputPath, quality);

            long compressTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                return new CompressResult(true, inputPath, outputPath,
                        originalSize, result.getCompressedSize(), "webp",
                        "WebP转换成功", compressTime);
            } else {
                return new CompressResult(false, inputPath, outputPath,
                        originalSize, 0, "webp",
                        "WebP转换失败", compressTime);
            }

        } catch (Exception e) {
            log.error("WebP转换异常: {}", e.getClass().getSimpleName());
            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "webp",
                    "转换异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

       
               
      
                             
                             
                              
                             
                    
       
    public static Map<String, CompressResult> generateImageVersions(
            String inputPath, String outputDir, String baseName,
            EnhancedImageCompressUtil.ImageSize... sizes) {

        Map<String, CompressResult> results = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            EnhancedImageCompressUtil.MultiVersionResult multiResult =
                    EnhancedImageCompressUtil.generateVersions(inputPath, outputDir, baseName, sizes);

            for (Map.Entry<String, String> entry : multiResult.getVersionPaths().entrySet()) {
                String version = entry.getKey();
                String path = entry.getValue();
                Long size = multiResult.getVersionSizes().get(version);

                results.put(version, new CompressResult(true, inputPath, path,
                        multiResult.getOriginalSize(), size, "image_version",
                        "版本生成成功", System.currentTimeMillis() - startTime));
            }

        } catch (Exception e) {
            log.error("生成多版本失败: {}", e.getClass().getSimpleName());
        }

        return results;
    }

                                                     

       
                   
      
                             
                             
                   
       
    public static CompressResult compressVideo(String inputPath, String outputPath) {
        return compressVideo(inputPath, outputPath, DEFAULT_VIDEO_CRF);
    }

       
                   
      
                             
                             
                                           
                   
       
    public static CompressResult compressVideo(String inputPath, String outputPath, int crf) {
        long startTime = System.currentTimeMillis();
        File inputFile = new File(inputPath);

        if (!inputFile.exists()) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "video",
                    "输入文件不存在", System.currentTimeMillis() - startTime);
        }

        long originalSize = inputFile.length();

        try {
                       
            VideoCompressUtil.CompressConfig config = new VideoCompressUtil.CompressConfig()
                    .setCrf(crf)
                    .setGenerateThumbnail(false);

            VideoCompressUtil.CompressResult result =
                    VideoCompressUtil.compress(inputPath, outputPath, config);

            long compressTime = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                return new CompressResult(true, inputPath, outputPath,
                        originalSize, result.getCompressedSize(), "video",
                        "视频压缩成功", compressTime);
            } else {
                return new CompressResult(false, inputPath, outputPath,
                        originalSize, 0, "video",
                        "视频压缩失败", compressTime);
            }

        } catch (Exception e) {
            log.error("视频压缩异常: {}", e.getClass().getSimpleName());
            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "video",
                    "压缩异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

       
                
      
                                  
                                  
                                    
                           
       
    public static CompressResult compressVideoWithThumbnail(
            String inputPath, String outputPath, String thumbnailOutput) {

        long startTime = System.currentTimeMillis();
        File inputFile = new File(inputPath);

        if (!inputFile.exists()) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "video",
                    "输入文件不存在", System.currentTimeMillis() - startTime);
        }

        long originalSize = inputFile.length();

        try {
            VideoCompressUtil.CompressConfig config = new VideoCompressUtil.CompressConfig()
                    .setCrf(DEFAULT_VIDEO_CRF)
                    .setGenerateThumbnail(true)
                    .setThumbnailTime(5);

            VideoCompressUtil.CompressResult result =
                    VideoCompressUtil.compress(inputPath, outputPath, config);

                   
            if (result.isSuccess()) {
                String thumbnailPath = VideoCompressUtil.generateThumbnail(
                        outputPath, thumbnailOutput, config.getThumbnailTime());

                long compressTime = System.currentTimeMillis() - startTime;

                return new CompressResult(true, inputPath, outputPath,
                        originalSize, result.getCompressedSize(), "video",
                        "视频压缩成功，封面: " + thumbnailPath, compressTime);
            }

            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "video",
                    "视频压缩失败", System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("视频压缩异常: {}", e.getClass().getSimpleName());
            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "video",
                    "压缩异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

       
             
      
                               
                                 
                                   
                            
       
    public static String generateVideoThumbnail(String videoPath, String outputPath, int timeSeconds) {
        try {
            return VideoCompressUtil.generateThumbnail(videoPath, outputPath, timeSeconds);
        } catch (Exception e) {
            log.error("生成封面失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

       
             
      
                            
                   
       
    public static VideoCompressUtil.VideoInfo getVideoInfo(String videoPath) {
        return VideoCompressUtil.getVideoInfo(videoPath);
    }

                                                     

       
                       
      
                             
                             
                                  
                   
       
       
                       
      
                             
                             
                                  
                   
       
    public static CompressResult compressAudio(String inputPath, String outputPath, int bitrate) {
        long startTime = System.currentTimeMillis();

                         
        if (!WorkProcessingUtil.isPathSafe(inputPath) || !WorkProcessingUtil.isPathSafe(outputPath)) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "audio",
                    "文件路径包含非法字符", System.currentTimeMillis() - startTime);
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            return new CompressResult(false, inputPath, outputPath, 0, 0, "audio",
                    "输入文件不存在", System.currentTimeMillis() - startTime);
        }

        long originalSize = inputFile.length();

        try {
                           
            if (!VideoCompressUtil.isFFmpegAvailable()) {
                return new CompressResult(false, inputPath, outputPath,
                        originalSize, 0, "audio",
                        "FFmpeg不可用", System.currentTimeMillis() - startTime);
            }

                       
            Files.createDirectories(Paths.get(outputPath).getParent());

                         
            List<String> commands = new ArrayList<>();
            commands.add(MediaToolPathResolver.ffmpeg());
            commands.add("-i");
            commands.add(inputPath);
            commands.add("-b:a");
            commands.add(bitrate + "k");
            commands.add("-y");
            commands.add(outputPath);

                                                                         
                                                  
            ProcessExecutionUtil.Result processResult =
                    ProcessExecutionUtil.execute(commands, 1800);
            int exitCode = processResult.isTimedOut() ? -1 : processResult.getExitCode();
            long compressTime = System.currentTimeMillis() - startTime;

            if (exitCode == 0) {
                long compressedSize = new File(outputPath).length();
                return new CompressResult(true, inputPath, outputPath,
                        originalSize, compressedSize, "audio",
                        "音频压缩成功", compressTime);
            } else {
                return new CompressResult(false, inputPath, outputPath,
                        originalSize, 0, "audio",
                        "FFmpeg执行失败，退出码: " + exitCode, compressTime);
            }

        } catch (Exception e) {
            log.error("音频压缩异常: {}", e.getClass().getSimpleName());
            return new CompressResult(false, inputPath, outputPath,
                    originalSize, 0, "audio",
                    "压缩异常: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

       
                      
      
                             
                             
                   
       
    public static CompressResult compressAudio(String inputPath, String outputPath) {
        return compressAudio(inputPath, outputPath, DEFAULT_AUDIO_BITRATE);
    }

                                                       

       
                                
      
                             
                             
                   
       
    public static CompressResult smartCompress(String inputPath, String outputPath) {
        String extension = getFileExtension(inputPath).toLowerCase();

        if (IMAGE_FORMATS.contains(extension)) {
            return compressImage(inputPath, outputPath);
        } else if (VIDEO_FORMATS.contains(extension)) {
            return compressVideo(inputPath, outputPath);
        } else if (AUDIO_FORMATS.contains(extension)) {
            return compressAudio(inputPath, outputPath);
        } else {
            return new CompressResult(false, inputPath, outputPath,
                    new File(inputPath).length(), 0, "unknown",
                    "不支持的文件类型: " + extension, 0);
        }
    }

                                                     

       
             
      
                               
                             
                     
       
    public static Map<String, CompressResult> batchCompress(File[] inputFiles, String outputDir) {
        Map<String, CompressResult> results = new HashMap<>();

        for (File inputFile : inputFiles) {
            String fileName = inputFile.getName();
            String outputPath = outputDir + File.separator + fileName;

            CompressResult result = smartCompress(inputFile.getAbsolutePath(), outputPath);
            results.put(fileName, result);
        }

        return results;
    }

       
                   
      
                               
                             
                                     
                     
       
    public static Map<String, CompressResult> batchCompressWithQuality(
            File[] inputFiles, String outputDir, float quality) {

        Map<String, CompressResult> results = new HashMap<>();

        for (File inputFile : inputFiles) {
            String fileName = inputFile.getName();
            String outputPath = outputDir + File.separator + fileName;

            String extension = getFileExtension(fileName).toLowerCase();
            CompressResult result;

            if (IMAGE_FORMATS.contains(extension)) {
                result = compressImage(inputFile.getAbsolutePath(), outputPath, quality);
            } else {
                result = smartCompress(inputFile.getAbsolutePath(), outputPath);
            }

            results.put(fileName, result);
        }

        return results;
    }

                                                     

       
              
      
                           
                       
       
    private static String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1);
        }
        return "";
    }

       
              
      
                       
                       
       
    private static String formatSize(long bytes) {
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

       
                  
      
                                 
                      
       
    public static float getRecommendedImageQuality(long fileSize) {
        return ImageCompressUtil.getRecommendedQuality(fileSize);
    }

       
                  
      
                                 
                      
       
    public static int getRecommendedVideoCrf(long fileSize) {
        if (fileSize < 10 * 1024 * 1024) {
            return 23;               
        } else if (fileSize < 50 * 1024 * 1024) {
            return 26;                 
        } else if (fileSize < 100 * 1024 * 1024) {
            return 28;                  
        } else {
            return 30;                
        }
    }

       
                   
      
                   
       
    public static boolean isFFmpegAvailable() {
        return VideoCompressUtil.isFFmpegAvailable();
    }

                                                     

       
             
       
    public static class CompressStats {
        private final int totalFiles;
        private final int successCount;
        private final int failureCount;
        private final long totalOriginalSize;
        private final long totalCompressedSize;
        private final long totalTime;

        public CompressStats(int totalFiles, int successCount, int failureCount,
                            long totalOriginalSize, long totalCompressedSize, long totalTime) {
            this.totalFiles = totalFiles;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalOriginalSize = totalOriginalSize;
            this.totalCompressedSize = totalCompressedSize;
            this.totalTime = totalTime;
        }

        public int getTotalFiles() { return totalFiles; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public double getSuccessRate() {
            return totalFiles > 0 ? (double) successCount / totalFiles * 100 : 0;
        }
        public long getTotalOriginalSize() { return totalOriginalSize; }
        public long getTotalCompressedSize() { return totalCompressedSize; }
        public double getTotalCompressionRatio() {
            return totalOriginalSize > 0 ?
                    (1.0 - (double) totalCompressedSize / totalOriginalSize) * 100 : 0;
        }
        public long getTotalTime() { return totalTime; }
        public double getAverageTime() {
            return totalFiles > 0 ? (double) totalTime / totalFiles : 0;
        }

        @Override
        public String toString() {
            return String.format(
                    "CompressStats{文件数=%d, 成功=%d(%.1f%%), 失败=%d, 原始大小=%s, 压缩后=%s, 压缩率=%.2f%%, 总耗时=%dms, 平均耗时=%.0fms}",
                    totalFiles, successCount, getSuccessRate(), failureCount,
                    formatSize(totalOriginalSize), formatSize(totalCompressedSize),
                    getTotalCompressionRatio(), totalTime, getAverageTime());
        }
    }

       
             
      
                            
                   
       
    public static CompressStats calculateStats(Map<String, CompressResult> results) {
        int totalFiles = results.size();
        int successCount = 0;
        int failureCount = 0;
        long totalOriginalSize = 0;
        long totalCompressedSize = 0;
        long totalTime = 0;

        for (CompressResult result : results.values()) {
            if (result.isSuccess()) {
                successCount++;
                totalOriginalSize += result.getOriginalSize();
                totalCompressedSize += result.getCompressedSize();
            } else {
                failureCount++;
            }
            totalTime += result.getCompressTime();
        }

        return new CompressStats(totalFiles, successCount, failureCount,
                totalOriginalSize, totalCompressedSize, totalTime);
    }
}
