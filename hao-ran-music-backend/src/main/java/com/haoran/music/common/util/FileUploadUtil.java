package com.haoran.music.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.config.SecurityConfig;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.service.VirusScanService;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;





@Slf4j
@Component
public class FileUploadUtil {


    @Value("${music.upload.avatar-path}")
    private String avatarPath;

    @Value("${music.upload.playlist-cover-path}")
    private String playlistCoverPath;

    @Value("${music.upload-url-prefix}")
    private String urlPrefix;


    @Value("${music.node3.host}")
    private String node3Host;

    @Value("${music.node3.user}")
    private String node3User;

    @Value("${music.node3.playlist-path}")
    private String node3PlaylistPath;

    @Value("${music.node3.images-path}")
    private String node3ImagesPath;


    @Value("${music.nginx-url-prefix}")
    private String nginxUrlPrefix;




    @Resource
    private SecurityConfig securityConfig;

    @Resource
    private MusicUploadConfig musicUploadConfig;

    @Resource
    private VirusScanService virusScanService;




    public String uploadAvatar(MultipartFile file) throws IOException {
        return uploadImage(file, avatarPath, "avatars", true, null, null);
    }







    public String uploadAvatar(MultipartFile file, Long userId, String username) throws IOException {
        return uploadImage(file, avatarPath, "avatars", true, userId, username);
    }




    public String uploadPlaylistCover(MultipartFile file) throws IOException {
        return uploadImage(file, playlistCoverPath, "playlist-covers", true, null, null);
    }







    public String uploadPlaylistCover(MultipartFile file, Long userId, String username) throws IOException {
        return uploadImage(file, playlistCoverPath, "playlist-covers", true, userId, username);
    }










    private String uploadImage(MultipartFile file, String uploadPath, String type, boolean syncToNode3,
                               Long userId, String username) throws IOException {
        validateImageFile(file);


        if (musicUploadConfig.isVirusScanEnabled() && virusScanService != null) {
            try {
                boolean isClean = virusScanService.scanInputStream(
                        file.getInputStream(),
                        file.getOriginalFilename()
                );
                if (!isClean) {
                    log.warn("event=managed_upload_virus_scan_rejected userId={}", userId);
                    throw new SecurityException("文件安全检测未通过，请上传安全的文件");
                }
                log.info("event=managed_upload_virus_scan_passed userId={}", userId);
            } catch (SecurityException e) {
                throw e;
            } catch (Exception e) {
                log.error("event=managed_upload_virus_scan_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());

                throw new SecurityException("virus scan failed, please try again later", e);
            }
        }

        File targetDir = new File(uploadPath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);


        String filename;
        if (userId != null && username != null) {

            String cleanUsername = username.replaceAll("[^a-zA-Z0-9_\\u4e00-\\u9fa5]", "_");
            filename = userId + "_" + cleanUsername + "_" + System.currentTimeMillis() + extension;
        } else {
            filename = UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + extension;
        }

        Path targetPath = Paths.get(uploadPath, filename);


        long fileSize = file.getSize();
        if (musicUploadConfig.isCompressEnabled() && fileSize > musicUploadConfig.getCompressThreshold()) {

            File tempFile = File.createTempFile("upload_", extension);
            file.transferTo(tempFile);

            try {

                float quality = ImageCompressUtil.getRecommendedQuality(fileSize);


                long compressedSize = ImageCompressUtil.compressImage(
                        tempFile.getAbsolutePath(),
                        targetPath.toString(),
                        quality
                );

                double ratio = (1.0 - (double) compressedSize / fileSize) * 100;
                log.info("event=managed_upload_image_compressed type={} originalBytes={} compressedBytes={} compressionPercent={}",
                        type, fileSize, compressedSize, ratio);

            } finally {

                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } else {

            Files.copy(file.getInputStream(), targetPath);
        }


        if (syncToNode3) {
            syncToNode3(targetPath.toString(), filename, type);
        }



        if (securityConfig.isHideIp()) {

            return "/" + type + "/" + filename;
        } else {

            return nginxUrlPrefix + type + "/" + filename;
        }
    }




    private void syncToNode3(String localFilePath, String filename, String type) {
        try {

            String node3TargetPath = node3ImagesPath + type;

            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(java.util.Arrays.asList(
                    "scp",
                    localFilePath,
                    node3User + "@" + node3Host + ":" + node3TargetPath + "/"), 120);
            if (result.isTimedOut() || result.getExitCode() != 0) {
                log.error("event=managed_upload_node3_sync_failed exitCode={}", result.getExitCode());
            } else {
                log.info("event=managed_upload_node3_sync_succeeded");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("event=managed_upload_node3_sync_interrupted errorType={}",
                    e.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("event=managed_upload_node3_sync_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }




    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String contentType = file.getContentType();
        if (!musicUploadConfig.isAllowedImageType(contentType)) {
            throw new IllegalArgumentException("只支持以下图片格式：JPG、PNG、GIF、WEBP");
        }

        long maxSize = musicUploadConfig.getImageMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("image size cannot exceed " + formatSize(maxSize));
        }
    }




    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ".jpg";
        }
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return ".jpg";
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }




    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }




    public boolean deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return false;
        }

        try {
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);


            File localFile = null;
            String type = null;
            if (fileUrl.contains("/avatars/")) {
                localFile = new File(avatarPath, filename);
                type = "avatars";
            } else if (fileUrl.contains("/playlist-covers/")) {
                localFile = new File(playlistCoverPath, filename);
                type = "playlist-covers";
            }

            if (localFile != null && localFile.exists()) {
                localFile.delete();
            }


            if (type != null) {
                deleteFromNode3(filename, type);
            }

            return true;
        } catch (Exception e) {
            log.error("event=managed_upload_delete_failed errorType={}", e.getClass().getSimpleName());
        }
        return false;
    }




    private void deleteFromNode3(String filename, String type) {
        try {

            if (!WorkProcessingUtil.isPathSafe(filename)) {
                log.error("event=managed_upload_delete_rejected reason=invalid_filename");
                return;
            }


            ProcessExecutionUtil.execute(java.util.Arrays.asList(
                    "ssh",
                    node3User + "@" + node3Host,
                    "rm", "-f", node3ImagesPath + type + "/" + filename), 30);
        } catch (Exception e) {
            log.error("event=managed_upload_node3_delete_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }





    public long getMaxImageSize() {
        return musicUploadConfig.getImageMaxFileSize();
    }

    public java.util.List<String> getAllowedImageTypes() {
        return musicUploadConfig.getAllowedImageTypes();
    }

    public java.util.List<String> getAllowedImageExtensions() {
        return musicUploadConfig.getAllowedImageExtensions();
    }

    public boolean isCompressEnabled() {
        return musicUploadConfig.isCompressEnabled();
    }




    public long getCompressThreshold() {
        return musicUploadConfig.getCompressThreshold();
    }
}
