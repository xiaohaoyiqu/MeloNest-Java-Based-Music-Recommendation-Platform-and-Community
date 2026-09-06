




package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.util.FileSecurityUtil;
import com.haoran.music.common.util.ImageCompressUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.service.VirusScanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;









@Slf4j
@Component
public class EmojiUploadUtil {





    @Value("${music.upload.emoji-path}")
    private String emojiPath;




    @Value("${music.node3.emoji-path}")
    private String node3EmojiPath;




    @Value("${music.node3.host}")
    private String node3Host;




    @Value("${music.node3.user}")
    private String node3User;




    @Resource
    private MusicUploadConfig musicUploadConfig;

    @Resource
    private VirusScanService virusScanService;





    @PostConstruct
    public void init() {
        try {
            Path storageRoot = Paths.get(emojiPath).toAbsolutePath().normalize();
            Path customDir = Files.createDirectories(storageRoot.resolve("custom"));
            Path tempDir = Files.createDirectories(storageRoot.resolve("temp"));
            if (!Files.isDirectory(customDir) || !Files.isDirectory(tempDir)
                    || !Files.isWritable(customDir) || !Files.isWritable(tempDir)) {
                throw new IOException("emoji storage directory is not writable");
            }
            long minimumFreeBytes = musicUploadConfig.getEmojiMaxFileSize()
                    * Math.max(1L, musicUploadConfig.getEmojiMaxFiles());
            if (Files.getFileStore(tempDir).getUsableSpace() < minimumFreeBytes) {
                throw new IOException("emoji storage has insufficient free space");
            }
            Path writeProbe = Files.createTempFile(tempDir, ".storage-check-", ".tmp");
            Files.delete(writeProbe);

            log.info("event=emoji_upload_utility_initialized compressEnabled={} retentionDays={}",
                    musicUploadConfig.isEmojiCompressEnabled(), musicUploadConfig.getEmojiRetentionDays());
        } catch (Exception e) {
            log.error("event=emoji_upload_utility_initialization_failed errorType={}",
                    e.getClass().getSimpleName());
            throw new IllegalStateException("emoji storage is unavailable", e);
        }
    }











    public String uploadEmoji(MultipartFile file, Long userId, String packageName) throws IOException {
        String generatedFileName = FileSecurityUtil.generateSafeFileName(
                file == null ? null : file.getOriginalFilename(), "emoji_" + userId);
        return uploadEmoji(file, userId, packageName, generatedFileName);
    }


    public String uploadEmoji(MultipartFile file, Long userId, String packageName,
                              String managedFileName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > musicUploadConfig.getEmojiMaxFileSize()) {
            throw new IllegalArgumentException("单个表情不能超过 "
                    + musicUploadConfig.getEmojiMaxFileSize() + " 字节");
        }
        if (!WorkProcessingUtil.isPathSafe(managedFileName)
                || managedFileName.indexOf('/') >= 0 || managedFileName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("非法的托管文件名");
        }

        File tempFile = null;
        File targetFile = null;
        String safeFileName = null;
        boolean node3SyncAttempted = false;
        boolean uploadCompleted = false;
        try {

            String tempFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            tempFile = new File(new File(emojiPath, "temp"), tempFileName);
            file.transferTo(tempFile);


            FileSecurityUtil.SecurityCheckResult result =
                    FileSecurityUtil.checkImageSecurity(tempFile, file.getOriginalFilename(), file.getContentType(),
                             musicUploadConfig.getEmojiMaxFileSize(),
                             musicUploadConfig.getAllowedImageTypes(),
                             musicUploadConfig.getAllowedImageExtensions(),
                             musicUploadConfig.getEmojiMaxWidth(),
                             musicUploadConfig.getEmojiMaxHeight());

            if (!result.isSafe()) {
                throw new IllegalArgumentException(result.getMessage());
            }


            if (musicUploadConfig.isEmojiVirusScanEnabled() && virusScanService != null) {
                try {
                    boolean isClean = virusScanService.scanFile(tempFile);
                    if (!isClean) {
                        log.warn("event=emoji_upload_virus_scan_rejected userId={}", userId);
                        throw new SecurityException("文件安全检测未通过，请上传安全的文件");
                    }
                    log.info("event=emoji_upload_virus_scan_completed userId={}", userId);
                } catch (SecurityException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("event=emoji_upload_virus_scan_failed userId={} errorType={}",
                            userId, e.getClass().getSimpleName());

                    throw new SecurityException("virus scan failed, please try again later", e);
                }
            }


            safeFileName = managedFileName;


            File targetDir = new File(emojiPath, "custom");
            targetFile = new File(targetDir, safeFileName);


            if (musicUploadConfig.isEmojiCompressEnabled() && result.getFileSize() > musicUploadConfig.getEmojiCompressThreshold()) {

                float quality = ImageCompressUtil.getRecommendedQuality(result.getFileSize());
                ImageCompressUtil.compressImage(tempFile.getAbsolutePath(),
                        targetFile.getAbsolutePath(), quality);
                log.info("event=emoji_image_compressed originalBytes={} compressedBytes={}",
                        result.getFileSize(), targetFile.length());
            } else {

                Files.copy(tempFile.toPath(), targetFile.toPath());
            }


            node3SyncAttempted = true;
            syncToNode3(targetFile, safeFileName);


            uploadCompleted = true;
            return "/emojis/custom/" + safeFileName;

        } finally {
            if (!uploadCompleted && targetFile != null) {
                if (node3SyncAttempted && safeFileName != null) {
                    deleteFromNode3(safeFileName);
                }
                try {
                    Files.deleteIfExists(targetFile.toPath());
                } catch (IOException e) {
                    log.warn("event=emoji_upload_local_rollback_failed errorType={}",
                            e.getClass().getSimpleName());
                }
            }

            if (tempFile != null && tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException e) {
                    log.warn("event=emoji_upload_temp_cleanup_deferred errorType={}",
                            e.getClass().getSimpleName());
                }
            }
        }
    }









    public String uploadPackageCover(MultipartFile file, Long userId) throws IOException {
        return uploadEmoji(file, userId, "cover");
    }








    public java.util.List<String> batchUploadEmojis(java.util.List<MultipartFile> files, Long userId) {
        java.util.List<String> result = new java.util.ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String path = uploadEmoji(file, userId, null);
                result.add(path);
            } catch (Exception e) {
                log.error("event=emoji_batch_upload_item_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
                result.forEach(this::deleteEmoji);
                throw new IllegalArgumentException("批量上传失败，已撤销本批次成功上传的文件", e);
            }
        }

        return result;
    }







    void syncToNode3(File localFile, String fileName) throws IOException {
        try {
            ProcessExecutionUtil.Result result = executeNode3SyncCommand(java.util.Arrays.asList(
                    "scp",
                    localFile.getAbsolutePath(),
                    node3User + "@" + node3Host + ":" + node3EmojiPath + "/custom/"));
            if (!result.isTimedOut() && result.getExitCode() == 0) {
                log.info("event=emoji_node3_sync_completed");
            } else {
                log.warn("event=emoji_node3_sync_failed exitCode={}", result.getExitCode());
                throw new IOException("node3 emoji synchronization failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("event=emoji_node3_sync_failed errorType={}", e.getClass().getSimpleName());
            throw new IOException("node3 emoji synchronization interrupted", e);
        } catch (IOException e) {
            log.error("event=emoji_node3_sync_failed errorType={}", e.getClass().getSimpleName());
            throw e;
        }
    }

    ProcessExecutionUtil.Result executeNode3SyncCommand(java.util.List<String> command)
            throws IOException, InterruptedException {
        return ProcessExecutionUtil.execute(command, 120);
    }







    public boolean deleteEmoji(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            return false;
        }

        try {
            if (imagePath.indexOf('\\') >= 0) {
                log.warn("event=emoji_image_delete_rejected reason=backslash_path");
                return false;
            }
            String fileName = imagePath.substring(imagePath.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || !WorkProcessingUtil.isPathSafe(fileName)) {
                log.warn("event=emoji_image_delete_rejected reason=invalid_filename");
                return false;
            }

            Path customRoot = Paths.get(emojiPath, "custom").toAbsolutePath().normalize();
            Path target = customRoot.resolve(fileName).normalize();
            if (!customRoot.equals(target.getParent()) || Files.isSymbolicLink(target)) {
                log.warn("event=emoji_image_delete_rejected reason=outside_managed_root");
                return false;
            }


            Files.deleteIfExists(target);


            if (!deleteFromNode3(fileName)) {
                return false;
            }

            log.info("event=emoji_image_delete_completed");
            return true;

        } catch (Exception e) {
            log.error("event=emoji_image_delete_failed errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }






    boolean deleteFromNode3(String fileName) {
        try {

            if (!WorkProcessingUtil.isPathSafe(fileName)) {
                log.error("event=emoji_node3_delete_rejected reason=invalid_filename");
                return false;
            }


            ProcessExecutionUtil.Result result = ProcessExecutionUtil.execute(java.util.Arrays.asList(
                    "ssh",
                    node3User + "@" + node3Host,
                    "rm", "-f", node3EmojiPath + "/custom/" + fileName), 30);
            if (!result.isFinished() || result.isTimedOut() || result.getExitCode() != 0) {
                log.error("event=emoji_node3_delete_failed exitCode={} timedOut={}",
                        result.getExitCode(), result.isTimedOut());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("event=emoji_node3_delete_failed errorType={}", e.getClass().getSimpleName());
            return false;
        }
    }






    public int cleanupTempFiles() {
        File tempDir = new File(emojiPath, "temp");
        if (!tempDir.exists()) {
            return 0;
        }

        int count = 0;
        long cutoffTime = System.currentTimeMillis() - (musicUploadConfig.getEmojiTempRetentionHours() * 60L * 60 * 1000);        

        File[] files = tempDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.lastModified() < cutoffTime) {
                    if (file.delete()) {
                        count++;
                    }
                }
            }
        }

        log.info("event=emoji_temp_cleanup_completed deletedCount={}", count);
        return count;
    }






    public String getEmojiPath() {
        return emojiPath;
    }






    public int getRetentionDays() {
        return musicUploadConfig.getEmojiRetentionDays();
    }
}
