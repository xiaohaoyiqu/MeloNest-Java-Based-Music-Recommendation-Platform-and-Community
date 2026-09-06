






package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;











@Slf4j
public class EnhancedImageCompressUtil {




    public enum ImageSize {
        THUMBNAIL("缩略图", 150, 150),
        SMALL("小图", 300, 300),
        MEDIUM("中图", 600, 600),
        LARGE("大图", 1200, 1200),
        ORIGINAL("原图", 0, 0);

        private final String name;
        private final int width;
        private final int height;

        ImageSize(String name, int width, int height) {
            this.name = name;
            this.width = width;
            this.height = height;
        }

        public String getName() { return name; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }




    public static class CompressResult {
        private final boolean success;
        private final String outputPath;
        private final long originalSize;
        private final long compressedSize;
        private final double compressionRatio;
        private final String format;

        public CompressResult(boolean success, String outputPath, long originalSize,
                              long compressedSize, String format) {
            this.success = success;
            this.outputPath = outputPath;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
            this.compressionRatio = originalSize > 0 ?
                (1.0 - (double) compressedSize / originalSize) * 100 : 0;
            this.format = format;
        }

        public boolean isSuccess() { return success; }
        public String getOutputPath() { return outputPath; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
        public double getCompressionRatio() { return compressionRatio; }
        public String getFormat() { return format; }
    }




    public static class MultiVersionResult {
        private final String originalPath;
        private final Map<String, String> versionPaths;
        private final long originalSize;
        private final Map<String, Long> versionSizes;

        public MultiVersionResult(String originalPath, Map<String, String> versionPaths,
                                 long originalSize, Map<String, Long> versionSizes) {
            this.originalPath = originalPath;
            this.versionPaths = versionPaths;
            this.originalSize = originalSize;
            this.versionSizes = versionSizes;
        }

        public String getOriginalPath() { return originalPath; }
        public Map<String, String> getVersionPaths() { return versionPaths; }
        public long getOriginalSize() { return originalSize; }
        public Map<String, Long> getVersionSizes() { return versionSizes; }
    }




    private static final float DEFAULT_QUALITY = 0.85f;
    private static final float HIGH_QUALITY = 0.95f;
    private static final float MEDIUM_QUALITY = 0.75f;
    private static final float LOW_QUALITY = 0.65f;




    private static final int MAX_WIDTH = 4096;
    private static final int MAX_HEIGHT = 4096;








    public static CompressResult compress(String inputPath, String outputPath) {
        return compress(inputPath, outputPath, DEFAULT_QUALITY, MAX_WIDTH, MAX_HEIGHT);
    }









    public static CompressResult compress(String inputPath, String outputPath, float quality) {
        return compress(inputPath, outputPath, quality, MAX_WIDTH, MAX_HEIGHT);
    }











    public static CompressResult compress(String inputPath, String outputPath,
                                         float quality, int maxWidth, int maxHeight) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            log.warn("event=image_compress_rejected reason=input_missing");
            return new CompressResult(false, outputPath, 0, 0, null);
        }

        long originalSize = inputFile.length();
        String format = getImageFormat(inputPath);

        try {

            Thumbnails.of(inputPath)
                    .size(maxWidth, maxHeight)
                    .outputQuality(quality)
                    .toFile(outputPath);

            File outputFile = new File(outputPath);
            long compressedSize = outputFile.length();

            log.info("event=image_compress_succeeded originalBytes={} compressedBytes={} compressionPercent={}",
                    originalSize, compressedSize,
                    (1.0 - (double) compressedSize / originalSize) * 100);

            return new CompressResult(true, outputPath, originalSize, compressedSize, format);

        } catch (IOException e) {
            log.error("event=image_compress_failed errorType={}", e.getClass().getSimpleName());
            return new CompressResult(false, outputPath, originalSize, 0, format);
        }
    }








    public static byte[] compress(byte[] imageData, String formatName) {
        return compress(imageData, formatName, DEFAULT_QUALITY);
    }









    public static byte[] compress(byte[] imageData, String formatName, float quality) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Thumbnails.of(bis)
                    .scale(1.0)
                    .outputQuality(quality)
                    .toOutputStream(bos);

            return bos.toByteArray();

        } catch (IOException e) {
            log.error("event=image_bytes_compress_failed errorType={}", e.getClass().getSimpleName());
            return imageData;
        }
    }










    public static MultiVersionResult generateVersions(String inputPath, String outputDir,
                                                     String baseName, ImageSize... sizes) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            log.warn("event=image_multi_version_rejected reason=input_missing");
            return new MultiVersionResult(inputPath, new HashMap<>(), 0, new HashMap<>());
        }

        long originalSize = inputFile.length();
        Map<String, String> versionPaths = new HashMap<>();
        Map<String, Long> versionSizes = new HashMap<>();


        new File(outputDir).mkdirs();

        for (ImageSize size : sizes) {
            if (size == ImageSize.ORIGINAL) {

                String originalOutput = outputDir + "/" + baseName + "_original." + getImageFormat(inputPath);
                try {
                    Files.copy(inputFile.toPath(), new File(originalOutput).toPath());
                    versionPaths.put("original", originalOutput);
                    versionSizes.put("original", originalSize);
                } catch (IOException e) {
                    log.error("event=image_original_copy_failed errorType={}", e.getClass().getSimpleName());
                }
            } else {

                String versionOutput = outputDir + "/" + baseName + "_" + size.name().toLowerCase() + "." + getImageFormat(inputPath);


                float quality = selectQualityBySize(size);

                CompressResult result = compress(inputPath, versionOutput, quality,
                        size.getWidth(), size.getHeight());

                if (result.isSuccess()) {
                    versionPaths.put(size.name().toLowerCase(), result.getOutputPath());
                    versionSizes.put(size.name().toLowerCase(), result.getCompressedSize());
                }
            }
        }

        log.info("event=image_multi_version_generation_succeeded versionCount={}", versionPaths.size());

        return new MultiVersionResult(inputPath, versionPaths, originalSize, versionSizes);
    }









    public static CompressResult convertToWebP(String inputPath, String outputPath, float quality) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            return new CompressResult(false, outputPath, 0, 0, "webp");
        }

        long originalSize = inputFile.length();

        try {

            Thumbnails.of(inputPath)
                    .outputQuality(quality)
                    .outputFormat("webp")
                    .toFile(outputPath);

            File outputFile = new File(outputPath);
            long compressedSize = outputFile.length();

            log.info("event=image_webp_conversion_succeeded compressionPercent={}",
                    (1.0 - (double) compressedSize / originalSize) * 100);

            return new CompressResult(true, outputPath, originalSize, compressedSize, "webp");

        } catch (IOException e) {
            log.error("event=image_webp_conversion_failed errorType={}", e.getClass().getSimpleName());
            return new CompressResult(false, outputPath, originalSize, 0, "webp");
        }
    }








    public static CompressResult smartCompress(String inputPath, String outputPath) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            return new CompressResult(false, outputPath, 0, 0, null);
        }

        long fileSize = inputFile.length();


        if (fileSize < 50 * 1024) {
            return compress(inputPath, outputPath, HIGH_QUALITY, MAX_WIDTH, MAX_HEIGHT);
        }

        else if (fileSize < 200 * 1024) {
            return compress(inputPath, outputPath, HIGH_QUALITY, 1920, 1920);
        }

        else if (fileSize < 1024 * 1024) {
            return compress(inputPath, outputPath, MEDIUM_QUALITY, 1920, 1920);
        }

        else if (fileSize < 5 * 1024 * 1024) {
            return compress(inputPath, outputPath, LOW_QUALITY, 1280, 1280);
        }

        else {
            return compress(inputPath, outputPath, LOW_QUALITY, 1024, 1024);
        }
    }








    public static Map<String, CompressResult> batchCompress(File[] inputFiles, String outputDir) {
        Map<String, CompressResult> results = new HashMap<>();

        new File(outputDir).mkdirs();

        for (File inputFile : inputFiles) {
            String fileName = inputFile.getName();
            String outputPath = outputDir + "/" + fileName;

            CompressResult result = smartCompress(inputFile.getAbsolutePath(), outputPath);
            results.put(fileName, result);
        }

        return results;
    }






    private static float selectQualityBySize(ImageSize size) {
        switch (size) {
            case THUMBNAIL:
                return 0.85f;
            case SMALL:
                return 0.90f;
            case MEDIUM:
                return HIGH_QUALITY;
            case LARGE:
                return HIGH_QUALITY;
            default:
                return DEFAULT_QUALITY;
        }
    }




    private static String getImageFormat(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        if (extension.equals("jpg")) {
            return "jpeg";
        }
        return extension;
    }




    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }




    public static float getRecommendedQuality(long fileSize) {
        if (fileSize < 100 * 1024) {
            return 0.95f;
        } else if (fileSize < 500 * 1024) {
            return 0.85f;
        } else if (fileSize < 1024 * 1024) {
            return 0.75f;
        } else {
            return 0.65f;
        }
    }
}
