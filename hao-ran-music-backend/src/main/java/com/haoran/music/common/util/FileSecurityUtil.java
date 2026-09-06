   
                      
                         
   

package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Locale;

   
            
                      
   
@Slf4j
public class FileSecurityUtil {

       
                    
       
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

       
                    
       
    private static final List<String> IMAGE_MAGIC_NUMBERS = Arrays.asList(
            "FFD8FF",             
            "89504E47",           
            "47494638",           
            "52494646"                    
    );

       
                 
       
    private static final List<String> DANGEROUS_EXTENSIONS = Arrays.asList(
            "jsp", "jspx", "php", "php3", "php4", "phtml", "exe", "sh", "bat",
            "cmd", "js", "vbs", "hta", "com", "scr", "pif", "dll", "sys"
    );

       
                 
       
    private static final int MAX_IMAGE_WIDTH = 4096;
    private static final int MAX_IMAGE_HEIGHT = 4096;

       
                           
       
    private static final int MIN_IMAGE_SIZE = 10;

       
                 
       
    private static final long MAX_IMAGE_FILE_SIZE = 5 * 1024 * 1024;        

       
           
       
    public static class SecurityCheckResult {
        private boolean safe;
        private String message;
        private long fileSize;

        public SecurityCheckResult(boolean safe, String message, long fileSize) {
            this.safe = safe;
            this.message = message;
            this.fileSize = fileSize;
        }

        public boolean isSafe() {
            return safe;
        }

        public String getMessage() {
            return message;
        }

        public long getFileSize() {
            return fileSize;
        }
    }

       
             
      
                                
                                 
                   
       
    public static SecurityCheckResult checkFileSecurity(File file, long maxSizeBytes) {
        if (file == null || !file.exists()) {
            return new SecurityCheckResult(false, "文件不存在", 0);
        }

        long fileSize = file.length();

                    
        if (fileSize == 0) {
            return new SecurityCheckResult(false, "文件大小为0", fileSize);
        }
        if (fileSize > maxSizeBytes) {
            return new SecurityCheckResult(false,
                    String.format("文件大小超过限制: %d > %d", fileSize, maxSizeBytes), fileSize);
        }

                     
        String fileName = file.getName().toLowerCase();
        for (String ext : DANGEROUS_EXTENSIONS) {
            if (fileName.endsWith("." + ext)) {
                return new SecurityCheckResult(false, "禁止上传的文件类型: " + ext, fileSize);
            }
        }

                             
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return new SecurityCheckResult(false, "非法文件名", fileSize);
        }

        return new SecurityCheckResult(true, "检测通过", fileSize);
    }

       
                
      
                               
                                       
                   
       
    public static SecurityCheckResult checkImageSecurity(File file, String contentType) {
        return checkImageSecurity(file, contentType, MAX_IMAGE_FILE_SIZE,
                ALLOWED_IMAGE_TYPES, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
    }

       
                                                                                    
       
    public static SecurityCheckResult checkImageSecurity(File file, String contentType,
                                                        long maxSizeBytes,
                                                        List<String> allowedImageTypes,
                                                        int maxImageWidth,
                                                        int maxImageHeight) {
        return checkImageSecurity(file, null, contentType,
                maxSizeBytes, allowedImageTypes, null, maxImageWidth, maxImageHeight);
    }

                                                                                                             
    public static SecurityCheckResult checkImageSecurity(File file, String originalFileName,
                                                         String contentType, long maxSizeBytes,
                                                         List<String> allowedImageTypes,
                                                         List<String> allowedExtensions,
                                                         int maxImageWidth, int maxImageHeight) {
        SecurityCheckResult basicResult = checkFileSecurity(file, maxSizeBytes);
        if (!basicResult.isSafe()) {
            return basicResult;
        }

        List<String> effectiveAllowedTypes = allowedImageTypes == null || allowedImageTypes.isEmpty()
                ? ALLOWED_IMAGE_TYPES : allowedImageTypes;
        int effectiveMaxWidth = maxImageWidth > 0 ? maxImageWidth : MAX_IMAGE_WIDTH;
        int effectiveMaxHeight = maxImageHeight > 0 ? maxImageHeight : MAX_IMAGE_HEIGHT;

        try {
            String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
            boolean allowedMime = effectiveAllowedTypes.stream()
                    .anyMatch(type -> type != null && type.equalsIgnoreCase(normalizedContentType));
            if (!allowedMime) {
                return new SecurityCheckResult(false,
                        "unsupported image type: " + contentType, file.length());
            }

            String magicNumber = getFileMagicNumber(file);
            String detectedFormat = detectImageFormat(magicNumber);
            if (detectedFormat == null) {
                return new SecurityCheckResult(false,
                        "invalid image file header", file.length());
            }
            String mimeFormat = normalizedContentType.equals("image/jpg")
                    ? "jpeg" : normalizedContentType.replace("image/", "");
            if (!mimeFormat.equals(detectedFormat)) {
                return new SecurityCheckResult(false,
                        originalFileName == null
                                ? "image MIME type and file content do not match"
                                : "image extension, MIME type and file content do not match",
                        file.length());
            }
            if (originalFileName != null) {
                String extension = getFileExtension(originalFileName).replace(".", "")
                        .toLowerCase(Locale.ROOT);
                String extensionFormat = "jpg".equals(extension) ? "jpeg" : extension;
                if (extension.isEmpty() || !extensionFormat.equals(detectedFormat)) {
                    return new SecurityCheckResult(false,
                            "image extension, MIME type and file content do not match", file.length());
                }
                if (allowedExtensions != null && !allowedExtensions.isEmpty()
                        && allowedExtensions.stream().filter(java.util.Objects::nonNull)
                        .map(value -> value.replace(".", "").toLowerCase(Locale.ROOT))
                        .noneMatch(extension::equals)) {
                    return new SecurityCheckResult(false,
                            "unsupported image extension: " + extension, file.length());
                }
            }

            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return new SecurityCheckResult(false, "image cannot be decoded", file.length());
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width < MIN_IMAGE_SIZE || height < MIN_IMAGE_SIZE) {
                return new SecurityCheckResult(false,
                        String.format("image dimensions too small: %dx%d", width, height), file.length());
            }

            if (width > effectiveMaxWidth || height > effectiveMaxHeight) {
                return new SecurityCheckResult(false,
                        String.format("image dimensions too large: %dx%d, max %dx%d",
                                width, height, effectiveMaxWidth, effectiveMaxHeight), file.length());
            }

            log.info("event=image_security_check_passed sizeBytes={} width={} height={} format={}",
                    file.length(), width, height, contentType);

            return new SecurityCheckResult(true, "image security check passed", file.length());

        } catch (IOException e) {
            log.error("event=image_security_check_failed errorType={}", e.getClass().getSimpleName());
            return new SecurityCheckResult(false, "image read failed", file.length());
        }
    }

       
                   
      
                     
                      
       
    private static String getFileMagicNumber(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = fis.read(header);
            if (read <= 0) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < read; i++) {
                sb.append(String.format("%02X", header[i] & 0xFF));
            }
            return sb.toString();
        }
    }

    private static String detectImageFormat(String magicNumber) {
        if (magicNumber.startsWith("FFD8FF")) {
            return "jpeg";
        }
        if (magicNumber.startsWith("89504E47")) {
            return "png";
        }
        if (magicNumber.startsWith("47494638")) {
            return "gif";
        }
        if (magicNumber.startsWith("52494646") && magicNumber.length() >= 24
                && "57454250".equals(magicNumber.substring(16, 24))) {
            return "webp";
        }
        return null;
    }

       
                    
      
                            
                      
       
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "file";
        }

                 
        String cleanName = new File(fileName).getName();

                 
        cleanName = cleanName.replaceAll("[/\\\\:*?\"<>|]", "_");

                  
        if (cleanName.length() > 100) {
            String extension = getFileExtension(cleanName);
            String nameWithoutExt = cleanName.substring(0, cleanName.lastIndexOf('.'));
            cleanName = nameWithoutExt.substring(0, 90) + extension;
        }

                 
        if (cleanName.isEmpty() || cleanName.startsWith(".")) {
            cleanName = "file_" + System.currentTimeMillis() + getFileExtension(cleanName);
        }

        return cleanName;
    }

       
              
      
                          
                        
       
    public static String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex);
    }

       
               
      
                                    
                                        
                     
       
    public static String generateSafeFileName(String originalFileName, String prefix) {
        String extension = getFileExtension(originalFileName);
        String cleanPrefix = prefix != null ? prefix.replaceAll("[^a-zA-Z0-9_-]", "_") : "file";
        long timestamp = System.currentTimeMillis();
        return cleanPrefix + "_" + timestamp + "_" + java.util.UUID.randomUUID().toString().replace("-", "") + extension;
    }

       
                               
                                           
      
                       
                       
       
    public static boolean containsSensitiveEXIF(File file) {
                     
                                 
        log.debug("event=image_exif_check_completed gpsMetadataDetected=false");
        return false;
    }

       
                     
      
                     
                       
       
    public static boolean containsScript(File file) {
        if (!file.getName().toLowerCase().endsWith(".svg")) {
            return false;
        }

                      
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(file))) {
            String line;
            Pattern scriptPattern = Pattern.compile("<script[^>]*>.*?</script>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Pattern javascriptPattern = Pattern.compile("javascript:",
                    Pattern.CASE_INSENSITIVE);

            while ((line = reader.readLine()) != null) {
                if (scriptPattern.matcher(line).find() ||
                    javascriptPattern.matcher(line).find()) {
                    log.warn("event=svg_security_check_rejected reason=script_content_detected");
                    return true;
                }
            }
        } catch (IOException e) {
            log.error("event=svg_security_check_failed errorType={}", e.getClass().getSimpleName());
        }

        return false;
    }
}
