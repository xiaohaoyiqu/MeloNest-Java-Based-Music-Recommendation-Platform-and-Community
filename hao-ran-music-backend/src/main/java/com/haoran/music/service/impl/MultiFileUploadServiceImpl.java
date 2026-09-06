










package com.haoran.music.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.haoran.music.common.config.MusicUploadConfig;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AudioQualityDetector;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.service.MultiFileUploadService;
import com.haoran.music.service.MediaAssetService;
import com.haoran.music.service.VirusScanService;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.common.util.UserAccountStatusUtil;
import lombok.extern.slf4j.Slf4j;
import com.haoran.music.common.util.FileSecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;




@Slf4j
@Service
public class MultiFileUploadServiceImpl implements MultiFileUploadService {

    @Autowired
    private VirusScanService virusScanService;

    @Autowired
    private AudioQualityDetector audioQualityDetector;

    @Autowired
    private MusicUploadConfig musicUploadConfig;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Value("${music.upload.multi-file.path}")
    private String uploadBasePath;

    @Value("${music.upload.nginx.url}")
    private String nginxUrl;



    private static final Set<String> AUDIO_FORMATS = new HashSet<>(Arrays.asList(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "ape"
    ));


    private static final Set<String> VIDEO_FORMATS = new HashSet<>(Arrays.asList(
        "mp4", "mov", "avi", "mkv", "flv", "wmv"
    ));


    private static final Set<String> LYRIC_FORMATS = new HashSet<>(Arrays.asList(
        "lrc", "txt", "json"
    ));


    private static final Set<String> IMAGE_FORMATS = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "webp"
    ));


    private static final Set<String> ARCHIVE_FORMATS = Collections.singleton("zip");

    @Autowired
    private RedisUtils redisUtils;

    private volatile Semaphore uploadSlots;

    private static final Set<String> SUPPORTED_UPLOAD_TYPES = new HashSet<>(Arrays.asList(
        "audio", "video", "lyric", "image"
    ));

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadMultipleFiles(List<MultipartFile> files, Long userId) {
        requireInteractiveUser(userId, "上传文件");
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "文件列表不能为空");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        return executeProtectedUpload(userId, files.size(), totalSize,
                () -> uploadMultipleFilesInternal(files, userId));
    }

    private Map<String, Object> uploadMultipleFilesInternal(List<MultipartFile> files, Long userId) {
        requireInteractiveUser(userId, "上传文件");
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> uploadedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();


        if (files == null || files.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "文件列表不能为空");
        }
        if (files.size() > musicUploadConfig.getMaxFiles()) {
            throw new BusinessException(ResultCode.ERROR, "最多支持" + musicUploadConfig.getMaxFiles() + "个文件");
        }


        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > musicUploadConfig.getMaxTotalSize()) {
            throw new BusinessException(ResultCode.ERROR, "总文件大小不能超过" + (musicUploadConfig.getMaxTotalSize() / 1024 / 1024) + "MB");
        }
        for (MultipartFile file : files) {
            if (file.getSize() > musicUploadConfig.getSingleFileMaxSize()) {
                throw new BusinessException(ResultCode.ERROR, "single file exceeds limit: "
                        + (musicUploadConfig.getSingleFileMaxSize() / 1024 / 1024) + "MB");
            }
        }



        LocalDate now = LocalDate.now();
        String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String userPath = String.valueOf(userId);
        String uploadDir = uploadBasePath + "/" + datePath + "/" + userPath;

        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.error("event=submission_upload_directory_create_failed errorType={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ERROR, "创建上传目录失败");
        }


        int successCount = 0;
        for (MultipartFile file : files) {
            try {
                Map<String, Object> fileResult = processSingleFile(file, uploadDir, datePath, userPath, userId);
                uploadedFiles.add(fileResult);
                successCount++;
            } catch (Exception e) {
                log.warn("event=submission_upload_file_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
                errors.add(toPublicUploadError(e));
            }
        }


        if (successCount == 0) {
            throw new BusinessException(ResultCode.ERROR, "所有文件上传失败: " + String.join("; ", errors));
        }
        result.put("success", errors.isEmpty());
        result.put("totalFiles", files.size());
        result.put("successCount", successCount);
        result.put("files", uploadedFiles);
        result.put("errors", errors);
        result.put("uploadTime", LocalDateTime.now());


        Map<String, Object> summary = generateFileSummary(uploadedFiles);
        result.put("summary", summary);

        log.info("event=submission_upload_batch_completed userId={} totalFiles={} successCount={}",
                userId, files.size(), successCount);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> uploadAndExtractZip(MultipartFile zipFile, Long userId) {
        requireInteractiveUser(userId, "上传压缩包");
        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "压缩包文件不能为空");
        }
        return executeProtectedUpload(userId, musicUploadConfig.getMaxFiles(), zipFile.getSize(),
                () -> uploadAndExtractZipInternal(zipFile, userId));
    }

    private Map<String, Object> uploadAndExtractZipInternal(MultipartFile zipFile, Long userId) {
        requireInteractiveUser(userId, "上传压缩包");
        Map<String, Object> result = new HashMap<>();
        List<Path> committedFiles = new ArrayList<>();

        if (zipFile == null || zipFile.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "压缩包文件不能为空");
        }

        String originalFilename = zipFile.getOriginalFilename();
        String extension = FileUtil.extName(originalFilename);
        if (extension == null) {
            throw new BusinessException(ResultCode.ERROR, "无法识别文件扩展名");
        }
        extension = extension.toLowerCase();

        if (!ARCHIVE_FORMATS.contains(extension)) {
            throw new BusinessException(ResultCode.ERROR, "不支持的压缩包格式，仅支持ZIP");
        }

        if (zipFile.getSize() > musicUploadConfig.getArchiveMaxFileSize()) {
            throw new BusinessException(ResultCode.ERROR, "archive file exceeds limit: "
                    + (musicUploadConfig.getArchiveMaxFileSize() / 1024 / 1024) + "MB");
        }

        String tempDir = uploadBasePath + "/temp/" + userId + "_" + System.currentTimeMillis();
        String archiveName = buildTempFileName(normalizeArchiveEntryName(originalFilename));
        String zipPath = tempDir + "/" + archiveName;

        try {
            Files.createDirectories(Paths.get(tempDir));
            zipFile.transferTo(new File(zipPath));

            if (musicUploadConfig.isVirusScanEnabled() && !scanUploadedFile(new File(zipPath), archiveName)) {
                throw new BusinessException(ResultCode.ERROR, "压缩包安全检测未通过: " + originalFilename);
            }

            List<Map<String, String>> extractedFiles = extractZip(zipPath, tempDir);

            validateExtractedFiles(extractedFiles);

            for (Map<String, String> file : extractedFiles) {
                String filePath = file.get("path");
                String fileName = file.get("name");
                File extractedFile = new File(filePath);

                if (musicUploadConfig.isVirusScanEnabled() && !scanUploadedFile(extractedFile, fileName)) {
                    throw new BusinessException(ResultCode.ERROR, "文件安全检测未通过: " + fileName);
                }

                if ("image".equals(identifyFileType(fileName))) {
                    FileSecurityUtil.SecurityCheckResult securityResult = FileSecurityUtil.checkImageSecurity(
                            extractedFile,
                            inferImageContentType(fileName),
                            musicUploadConfig.getImageMaxFileSize(),
                            musicUploadConfig.getAllowedImageTypes(),
                            musicUploadConfig.getImageMaxWidth(),
                            musicUploadConfig.getImageMaxHeight());
                    if (!securityResult.isSafe()) {
                        throw new BusinessException(ResultCode.ERROR,
                                "文件安全检测未通过: " + fileName + ", 原因: " + securityResult.getMessage());
                    }
                }
            }

            LocalDate now = LocalDate.now();
            String datePath = now.format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String userPath = String.valueOf(userId);
            String finalDir = uploadBasePath + "/" + datePath + "/" + userPath;
            Files.createDirectories(Paths.get(finalDir));

            List<Map<String, Object>> movedFiles = new ArrayList<>();
            for (Map<String, String> file : extractedFiles) {
                String sourcePath = file.get("path");
                String fileName = file.get("name");
                String safeFileName = IdUtil.simpleUUID() + "." + FileUtil.extName(fileName);
                String targetPath = finalDir + "/" + safeFileName;

                Files.move(Paths.get(sourcePath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
                committedFiles.add(Paths.get(targetPath));

                String fileUrl = buildPublicUrl(datePath, userPath, safeFileName);
                try {
                    mediaAssetService.registerStagedSubmissionAsset(userId, identifyFileType(fileName), fileName,
                            fileUrl, Paths.get(targetPath).toAbsolutePath().normalize().toString(),
                            new File(targetPath).length());
                } catch (RuntimeException e) {
                    Files.deleteIfExists(Paths.get(targetPath));
                    throw e;
                }
                Map<String, Object> movedFile = new HashMap<>();
                movedFile.put("originalName", fileName);
                movedFile.put("name", fileName);
                movedFile.put("url", fileUrl);
                movedFile.put("type", identifyFileType(fileName));
                movedFile.put("size", new File(targetPath).length());
                movedFiles.add(movedFile);
            }

            result.put("success", true);
            result.put("totalFiles", movedFiles.size());
            result.put("files", movedFiles);
            result.put("categorized", categorizeUploadedFiles(movedFiles));
            result.put("summary", generateUploadedFileSummary(movedFiles));
            result.put("uploadTime", LocalDateTime.now());

            log.info("event=submission_archive_extract_completed userId={} totalFiles={}", userId, movedFiles.size());
            return result;
        } catch (BusinessException e) {
            deleteFilesQuietly(committedFiles, userId);
            throw e;
        } catch (Exception e) {
            deleteFilesQuietly(committedFiles, userId);
            log.error("event=submission_archive_extract_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ERROR, "压缩包处理失败，请稍后重试");
        } finally {
            try {
                FileUtil.del(new File(tempDir));
            } catch (Exception e) {
                log.warn("event=submission_archive_temp_cleanup_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
            }
        }
    }



    private List<Map<String, String>> extractZip(String zipPath, String destDir) throws IOException {
        List<Map<String, String>> files = new ArrayList<>();
        long totalExtractedSize = 0L;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }

                    String fileName = normalizeArchiveEntryName(entry.getName());
                    if (StrUtil.isBlank(fileName)) {
                        continue;
                    }

                    validateSupportedUploadFile(fileName, 0L, false);
                    if (files.size() >= musicUploadConfig.getMaxFiles()) {
                        throw new BusinessException(ResultCode.ERROR,
                                "压缩包内文件数量不能超过" + musicUploadConfig.getMaxFiles() + "个");
                    }

                    String tempFileName = buildTempFileName(fileName);
                    Path outputPath = Paths.get(destDir, tempFileName).normalize();
                    Path destPath = Paths.get(destDir).toAbsolutePath().normalize();
                    if (!outputPath.toAbsolutePath().normalize().startsWith(destPath)) {
                        throw new BusinessException(ResultCode.ERROR, "压缩包内存在非法路径: " + entry.getName());
                    }

                    long fileSize = 0L;
                    try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fileSize += len;
                            totalExtractedSize += len;
                            if (fileSize > musicUploadConfig.getSingleFileMaxSize()) {
                                throw new BusinessException(ResultCode.ERROR,
                                        "解压文件超过单文件限制: " + fileName);
                            }
                            if (totalExtractedSize > musicUploadConfig.getMaxTotalSize()) {
                                throw new BusinessException(ResultCode.ERROR, "压缩包解压后总大小超过限制");
                            }
                            fos.write(buffer, 0, len);
                        }
                    }

                    validateSupportedUploadFile(fileName, fileSize, true);
                    Map<String, String> fileMap = new HashMap<>();
                    fileMap.put("name", fileName);
                    fileMap.put("path", outputPath.toString());
                    fileMap.put("size", String.valueOf(fileSize));
                    files.add(fileMap);
                } finally {
                    zis.closeEntry();
                }
            }
        }

        return files;
    }
    private boolean scanUploadedFile(File file, String fileName) {
        try {
            return virusScanService.scanFile(file);
        } catch (Exception e) {
            log.warn("event=submission_virus_scan_unavailable errorType={}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.ERROR, "文件安全检测服务暂时不可用，请稍后再试");
        }
    }

    private String toPublicUploadError(Exception error) {
        if (!(error instanceof BusinessException) || error.getMessage() == null) {
            return "文件处理失败，请稍后再试";
        }
        String message = error.getMessage().trim();
        if (message.contains("virus scan failed") || message.contains("安全检测服务暂时不可用")) {
            return "文件安全检测服务暂时不可用，请稍后再试";
        }
        if (message.contains("security scan failed") || message.contains("安全检测未通过")) {
            return "文件安全检测未通过";
        }
        if (message.startsWith("文件") || message.startsWith("不支持") || message.contains("大小不能超过")) {
            return message;
        }
        return "文件处理失败，请稍后再试";
    }

    private void validateExtractedFiles(List<Map<String, String>> extractedFiles) {
        if (extractedFiles == null || extractedFiles.isEmpty()) {
            throw new BusinessException(ResultCode.ERROR, "压缩包内没有可处理的文件");
        }
        if (extractedFiles.size() > musicUploadConfig.getMaxFiles()) {
            throw new BusinessException(ResultCode.ERROR,
                    "压缩包内文件数量不能超过" + musicUploadConfig.getMaxFiles() + "个");
        }

        long totalSize = 0L;
        for (Map<String, String> file : extractedFiles) {
            String fileName = file.get("name");
            long size;
            try {
                size = Long.parseLong(file.get("size"));
            } catch (Exception e) {
                size = new File(file.get("path")).length();
                file.put("size", String.valueOf(size));
            }
            validateSupportedUploadFile(fileName, size, true);
            totalSize += size;
            if (totalSize > musicUploadConfig.getMaxTotalSize()) {
                throw new BusinessException(ResultCode.ERROR, "压缩包解压后总大小超过限制");
            }
        }
    }

    private void validateSupportedUploadFile(MultipartFile file) {
        String fileName = file != null ? file.getOriginalFilename() : null;
        long size = file != null ? file.getSize() : 0L;
        validateSupportedUploadFile(fileName, size, true);
    }

    private void validateSupportedUploadFile(String fileName, long size, boolean checkSize) {
        if (StrUtil.isBlank(fileName)) {
            throw new BusinessException(ResultCode.ERROR, "文件名不能为空");
        }
        String extension = FileUtil.extName(fileName);
        if (extension == null) {
            throw new BusinessException(ResultCode.ERROR, "无法识别文件扩展名: " + fileName);
        }
        extension = extension.toLowerCase();
        String type = identifyFileType(fileName);
        if (ARCHIVE_FORMATS.contains(extension)) {
            throw new BusinessException(ResultCode.ERROR, "压缩包请使用压缩包上传入口: " + fileName);
        }
        if (!SUPPORTED_UPLOAD_TYPES.contains(type)) {
            throw new BusinessException(ResultCode.ERROR, "不支持的文件类型: " + fileName);
        }
        if (checkSize && size <= 0) {
            throw new BusinessException(ResultCode.ERROR, "文件大小为0: " + fileName);
        }
        if (checkSize && size > musicUploadConfig.getSingleFileMaxSize()) {
            throw new BusinessException(ResultCode.ERROR, "single file exceeds limit: "
                    + (musicUploadConfig.getSingleFileMaxSize() / 1024 / 1024) + "MB");
        }
    }

    private String normalizeArchiveEntryName(String entryName) {
        if (StrUtil.isBlank(entryName)) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw new BusinessException(ResultCode.ERROR, "压缩包内存在非法路径: " + entryName);
        }
        if (normalized.contains("/.") || normalized.contains("__MACOSX")) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (StrUtil.isBlank(fileName) || fileName.startsWith(".")) {
            return null;
        }
        return fileName;
    }

    private String buildTempFileName(String fileName) {
        String extension = FileUtil.extName(fileName);
        if (extension == null) {
            throw new BusinessException(ResultCode.ERROR, "无法识别文件扩展名: " + fileName);
        }
        return IdUtil.simpleUUID() + "." + extension.toLowerCase();
    }

    private String inferImageContentType(String fileName) {
        String extension = FileUtil.extName(fileName);
        if (extension == null) {
            return null;
        }
        extension = extension.toLowerCase();
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return "image/jpeg";
        }
        if ("png".equals(extension)) {
            return "image/png";
        }
        if ("gif".equals(extension)) {
            return "image/gif";
        }
        if ("webp".equals(extension)) {
            return "image/webp";
        }
        return null;
    }

    private void collectExtractedRegularFiles(File dir, String archivePath, List<Map<String, String>> files) {
        try {
            Path root = dir.toPath().toAbsolutePath().normalize().toRealPath(LinkOption.NOFOLLOW_LINKS);
            collectExtractedRegularFiles(root, dir.toPath(), Paths.get(archivePath).toAbsolutePath().normalize(), files);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.ERROR, "解压目录安全校验失败");
        }
    }

    private void collectExtractedRegularFiles(Path root, Path dir, Path archivePath,
                                              List<Map<String, String>> files) throws IOException {
        File[] children = dir.toFile().listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            Path candidate = child.toPath().toAbsolutePath().normalize();
            if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "压缩包包含不安全链接或越界路径");
            }
            if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                collectExtractedRegularFiles(root, candidate, archivePath, files);
                continue;
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) || candidate.equals(archivePath)) {
                continue;
            }
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(root) || hasMultipleHardLinks(candidate)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "压缩包包含不安全链接或越界路径");
            }
            String fileName = normalizeArchiveEntryName(child.getName());
            if (StrUtil.isBlank(fileName)) {
                continue;
            }
            Map<String, String> fileMap = new HashMap<>();
            fileMap.put("name", fileName);
            fileMap.put("path", realCandidate.toString());
            fileMap.put("size", String.valueOf(child.length()));
            files.add(fileMap);
        }
    }

    private boolean hasMultipleHardLinks(Path path) {
        try {
            Object count = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            return count instanceof Number && ((Number) count).longValue() > 1L;
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException ignored) {
            return false;
        }
    }

    private Map<String, List<Map<String, Object>>> categorizeUploadedFiles(List<Map<String, Object>> files) {
        Map<String, List<Map<String, Object>>> categorized = new HashMap<>();
        categorized.put("audio", new ArrayList<>());
        categorized.put("video", new ArrayList<>());
        categorized.put("lyric", new ArrayList<>());
        categorized.put("image", new ArrayList<>());
        categorized.put("other", new ArrayList<>());

        for (Map<String, Object> file : files) {
            String type = (String) file.get("type");
            if (!categorized.containsKey(type)) {
                type = "other";
            }
            categorized.get(type).add(file);
        }
        return categorized;
    }

    private Map<String, Object> generateUploadedFileSummary(List<Map<String, Object>> files) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        long totalSize = 0L;

        typeCount.put("audio", 0);
        typeCount.put("video", 0);
        typeCount.put("lyric", 0);
        typeCount.put("image", 0);
        typeCount.put("other", 0);

        for (Map<String, Object> file : files) {
            String type = (String) file.get("type");
            if (!typeCount.containsKey(type)) {
                type = "other";
            }
            typeCount.put(type, typeCount.get(type) + 1);
            totalSize += ((Number) file.get("size")).longValue();
        }

        summary.put("typeCount", typeCount);
        summary.put("totalSize", totalSize);
        summary.put("totalFiles", files.size());
        return summary;
    }
    @Override
    public String identifyFileType(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "other";
        }

        String extension = FileUtil.extName(fileName);        if (extension == null) {            return "other";        }        extension = extension.toLowerCase();

        if (AUDIO_FORMATS.contains(extension)) {
            return "audio";
        } else if (VIDEO_FORMATS.contains(extension)) {
            return "video";
        } else if (LYRIC_FORMATS.contains(extension)) {
            return "lyric";
        } else if (IMAGE_FORMATS.contains(extension)) {
            return "image";
        } else {
            return "other";
        }
    }

    @Override
    public List<Map<String, Object>> batchDetectAudioQuality(List<String> audioUrls, Long userId) {
        requireInteractiveUser(userId, "检测音频");
        requireDetectionBatch(audioUrls);
        List<Map<String, Object>> results = new ArrayList<>();

        for (String url : audioUrls) {
            try {
                Path ownedFile = requireOwnedManagedFile(url, userId);
                AudioQualityDetector.AudioInfo info = audioQualityDetector.detectAudioInfo(ownedFile.toString());
                Map<String, Object> result = new HashMap<>();
                result.put("url", url);
                result.put("qualityLevel", info.getQualityLevel());
                result.put("qualityName", info.getQualityName());
                result.put("fileSize", info.getFileSize());
                result.put("duration", info.getDuration());
                result.put("bitrate", info.getBitrate());
                result.put("sampleRate", info.getSampleRate());
                result.put("format", info.getFormat());
                results.add(result);
            } catch (Exception e) {
                Map<String, Object> result = new HashMap<>();
                result.put("url", url);
                result.put("error", "音频检测失败");
                results.add(result);
            }
        }

        return results;
    }




    @Override
    public List<Map<String, Object>> batchDetectVideoInfo(List<String> videoUrls, Long userId) {
        requireInteractiveUser(userId, "检测视频");
        requireDetectionBatch(videoUrls);
        List<Map<String, Object>> results = new ArrayList<>();

        for (String url : videoUrls) {
            Map<String, Object> result = new HashMap<>();
            result.put("url", url);

            try {

                Path ownedFile = requireOwnedManagedFile(url, userId);


                Map<String, Object> videoInfo = detectVideoWithFFprobe(ownedFile.toString());
                result.put("status", "success");
                result.putAll(videoInfo);

            } catch (Exception e) {
                log.warn("event=submission_video_detection_failed errorType={}", e.getClass().getSimpleName());
                result.put("status", "error");
                result.put("error", "视频检测失败");
            }

            results.add(result);
        }

        log.info("event=submission_video_detection_batch_completed totalFiles={} successCount={}", videoUrls.size(),
            results.stream().filter(r -> "success".equals(r.get("status"))).count());
        return results;
    }




    private String extractLocalPath(String url) {
        return CommonUtil.extractLocalPath(url);
    }

    private void requireInteractiveUser(Long userId, String action) {
        User user = userId == null ? null : userMapper.selectById(userId);
        UserAccountStatusUtil.requireCanInteract(user, action);
    }

    private Map<String, Object> executeProtectedUpload(Long userId, int fileCount, long bytes,
                                                        Supplier<Map<String, Object>> action) {
        Semaphore slots = getUploadSlots();
        if (!slots.tryAcquire()) {
            throw new BusinessException(429, "上传任务繁忙，请稍后重试");
        }
        String lockKey = "upload:submission:processing:" + userId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = false;
        try {
            locked = redisUtils.setIfAbsent(lockKey, lockValue,
                    Math.max(1, musicUploadConfig.getSubmissionProcessingMinutes()), TimeUnit.MINUTES);
            if (!locked) {
                throw new BusinessException(429, "你已有上传任务正在处理，请稍后再试");
            }
            reserveDailyQuota(userId, fileCount, bytes);
            return action.get();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("event=submission_upload_guard_unavailable userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            throw new BusinessException(503, "上传保护暂不可用，请稍后重试");
        } finally {
            if (locked) {
                try {
                    redisUtils.compareAndDelete(lockKey, lockValue);
                } catch (RuntimeException e) {
                    log.warn("event=submission_upload_lock_release_deferred userId={} errorType={}",
                            userId, e.getClass().getSimpleName());
                }
            }
            slots.release();
        }
    }

    private void reserveDailyQuota(Long userId, int fileCount, long bytes) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileKey = "upload:submission:daily:files:" + userId + ":" + date;
        String byteKey = "upload:submission:daily:bytes:" + userId + ":" + date;
        long safeFiles = Math.max(1, fileCount);
        long safeBytes = Math.max(0L, bytes);
        Long currentFiles = redisUtils.increment(fileKey, safeFiles);
        try {
            Long currentBytes = redisUtils.increment(byteKey, safeBytes);
            if (Long.valueOf(safeFiles).equals(currentFiles)) {
                redisUtils.expire(fileKey, 2, TimeUnit.DAYS);
            }
            if (Long.valueOf(safeBytes).equals(currentBytes)) {
                redisUtils.expire(byteKey, 2, TimeUnit.DAYS);
            }
            if (currentFiles == null || currentBytes == null
                    || currentFiles > musicUploadConfig.getSubmissionDailyMaxFilesPerUser()
                    || currentBytes > musicUploadConfig.getSubmissionDailyMaxBytesPerUser()) {
                rollbackQuota(fileKey, byteKey, safeFiles, safeBytes);
                throw new BusinessException(429, "今日上传额度已用完，请明天再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            rollbackQuota(fileKey, byteKey, safeFiles, safeBytes);
            throw e;
        }
    }

    private void deleteFilesQuietly(List<Path> paths, Long userId) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                log.warn("event=submission_committed_file_cleanup_failed userId={} errorType={}",
                        userId, e.getClass().getSimpleName());
            }
        }
    }

    private void rollbackQuota(String fileKey, String byteKey, long files, long bytes) {
        try {
            redisUtils.decrement(fileKey, files);
            redisUtils.decrement(byteKey, bytes);
        } catch (RuntimeException e) {
            log.warn("event=submission_upload_quota_rollback_deferred errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private Semaphore getUploadSlots() {
        Semaphore current = uploadSlots;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (uploadSlots == null) {
                uploadSlots = new Semaphore(
                        Math.max(1, musicUploadConfig.getSubmissionMaxConcurrentUploads()), true);
            }
            return uploadSlots;
        }
    }

    private void requireDetectionBatch(List<String> urls) {
        int max = Math.min(Math.max(musicUploadConfig.getMaxFiles(), 1), 20);
        if (urls == null || urls.isEmpty() || urls.size() > max) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "检测文件数量必须在1到" + max + "之间");
        }
    }

    private Path requireOwnedManagedFile(String url, Long userId) {
        if (StrUtil.isBlank(url) || url.indexOf('\0') >= 0 || url.indexOf('\\') >= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "文件地址不合法");
        }
        String localPath = extractLocalPath(url);
        if (StrUtil.isBlank(localPath)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能检测本人上传的受管文件");
        }
        try {
            Path root = Paths.get(uploadBasePath).toAbsolutePath().normalize().toRealPath();
            Path candidate = Paths.get(localPath).toAbsolutePath().normalize();
            if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能检测本人上传的受管文件");
            }
            Path relative = root.relativize(candidate);
            if (relative.getNameCount() < 4 || !String.valueOf(userId).equals(relative.getName(2).toString())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能检测本人上传的受管文件");
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root) || !Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "只能检测本人上传的受管文件");
            }
            return real;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能检测本人上传的受管文件");
        }
    }

    private String buildPublicUrl(String datePath, String userPath, String fileName) {
        String prefix = nginxUrl == null ? "" : nginxUrl.trim();
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + datePath + "/" + userPath + "/" + fileName;
    }




    private Map<String, Object> detectVideoWithFFprobe(String localPath) {
        Map<String, Object> info = new HashMap<>();

        File file = new File(localPath);
        info.put("fileSize", file.length());

        try {
            com.haoran.music.common.util.ProcessExecutionUtil.Result processResult =
                    com.haoran.music.common.util.ProcessExecutionUtil.execute(Arrays.asList(
                            com.haoran.music.common.util.MediaToolPathResolver.ffprobe(), "-v", "quiet",
                            "-print_format", "json",
                            "-show_streams", "-show_format", localPath), 30);
            if (processResult.isTimedOut()) {
                info.put("status", "timeout");
                return info;
            }

            if (processResult.getExitCode() == 0) {
                parseVideoInfo(processResult.getOutput(), info);
            }

        } catch (Exception e) {
            log.warn("event=submission_ffprobe_failed errorType={}", e.getClass().getSimpleName());
        }

        return info;
    }




    private void parseVideoInfo(String jsonOutput, Map<String, Object> info) {

        Double duration = extractDouble(jsonOutput, "\"duration\"\\s*:\\s*(\\d+\\.?\\d*)");
        if (duration != null) {
            info.put("duration", duration.intValue());
        }


        Integer width = extractInt(jsonOutput, "\"width\"\\s*:\\s*(\\d+)");
        Integer height = extractInt(jsonOutput, "\"height\"\\s*:\\s*(\\d+)");

        if (width != null && height != null) {
            info.put("width", width);
            info.put("height", height);
            info.put("quality", determineVideoQuality(width, height));
        }


        String codec = extractString(jsonOutput, "\"codec_name\"\\s*:\\s*\"([^\"]+)\"");
        if (codec != null) {
            info.put("format", codec);
        }


        Long bitrate = extractLong(jsonOutput, "\"bit_rate\"\\s*:\\s*(\\d+)");
        if (bitrate != null) {
            info.put("bitrate", bitrate.intValue());
        }
    }




    private String determineVideoQuality(Integer width, Integer height) {
        int minDim = Math.min(width, height);
        if (minDim >= 2160) return "4k";
        if (minDim >= 1080) return "1080p";
        if (minDim >= 720) return "720p";
        if (minDim >= 480) return "480p";
        return "360p";
    }




    private Integer extractInt(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {

        }
        return null;
    }




    private Double extractDouble(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        } catch (Exception e) {

        }
        return null;
    }




    private String extractString(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {

        }
        return null;
    }




    private Long extractLong(String text, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Map<String, List<Map<String, Object>>> categorizeExtractedFiles(List<Map<String, String>> extractedFiles) {
        Map<String, List<Map<String, Object>>> categorized = new HashMap<>();
        categorized.put("audio", new ArrayList<>());
        categorized.put("video", new ArrayList<>());
        categorized.put("lyric", new ArrayList<>());
        categorized.put("image", new ArrayList<>());
        categorized.put("other", new ArrayList<>());

        for (Map<String, String> file : extractedFiles) {
            String fileName = file.get("name");
            String type = identifyFileType(fileName);

            Map<String, Object> fileObj = new HashMap<>();
            fileObj.put("name", fileName);
            fileObj.put("path", file.get("path"));
            fileObj.put("size", file.get("size"));

            categorized.get(type).add(fileObj);
        }

        return categorized;
    }

    @Override
    public Map<String, Object> generateContentSummary(List<Map<String, String>> files) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        long totalSize = 0;

        typeCount.put("audio", 0);
        typeCount.put("video", 0);
        typeCount.put("lyric", 0);
        typeCount.put("image", 0);
        typeCount.put("other", 0);

        for (Map<String, String> file : files) {
            String type = identifyFileType(file.get("name"));
            typeCount.put(type, typeCount.get(type) + 1);
            try {
                totalSize += Long.parseLong(file.get("size"));
            } catch (Exception e) {

            }
        }

        summary.put("typeCount", typeCount);
        summary.put("totalSize", totalSize);
        summary.put("totalFiles", files.size());

        return summary;
    }




    private Map<String, Object> processSingleFile(MultipartFile file, String uploadDir,
                                                   String datePath, String userPath, Long userId) {
        String originalFilename = file.getOriginalFilename();
        validateSupportedUploadFile(file);

        String extension = FileUtil.extName(originalFilename).toLowerCase();
        String fileType = identifyFileType(originalFilename);
        String safeFileName = IdUtil.simpleUUID() + "." + extension;
        String filePath = uploadDir + "/" + safeFileName;
        File savedFile = new File(filePath);

        try {
            file.transferTo(savedFile);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.ERROR, "文件保存失败，请稍后重试");
        }

        try {
            if (musicUploadConfig.isVirusScanEnabled() && !scanUploadedFile(savedFile, originalFilename)) {
                savedFile.delete();
                throw new BusinessException(ResultCode.ERROR, "文件安全检测未通过");
            }

            if ("image".equals(fileType)) {
                FileSecurityUtil.SecurityCheckResult securityResult = FileSecurityUtil.checkImageSecurity(
                        savedFile,
                        file.getContentType(),
                        musicUploadConfig.getImageMaxFileSize(),
                        musicUploadConfig.getAllowedImageTypes(),
                        musicUploadConfig.getImageMaxWidth(),
                        musicUploadConfig.getImageMaxHeight());
                if (!securityResult.isSafe()) {
                    savedFile.delete();
                    throw new BusinessException(ResultCode.ERROR, "文件安全检测未通过");
                }
            }
        } catch (BusinessException e) {
            savedFile.delete();
            throw e;
        }

        String fileUrl = buildPublicUrl(datePath, userPath, safeFileName);
        try {
            mediaAssetService.registerStagedSubmissionAsset(userId, fileType, originalFilename, fileUrl,
                    savedFile.toPath().toAbsolutePath().normalize().toString(), savedFile.length());
        } catch (RuntimeException e) {
            savedFile.delete();
            throw e;
        }

        Map<String, Object> qualityInfo = null;
        if ("audio".equals(fileType)) {
            try {
                AudioQualityDetector.AudioInfo info = audioQualityDetector.detectAudioInfo(filePath);
                qualityInfo = new HashMap<>();
                qualityInfo.put("qualityLevel", info.getQualityLevel());
                qualityInfo.put("qualityName", info.getQualityName());
                qualityInfo.put("fileSize", info.getFileSize());
                qualityInfo.put("duration", info.getDuration());
                qualityInfo.put("bitrate", info.getBitrate());
                qualityInfo.put("sampleRate", info.getSampleRate());
                qualityInfo.put("format", info.getFormat());
            } catch (Exception e) {
                log.warn("event=submission_audio_detection_failed errorType={}", e.getClass().getSimpleName());
            }
        }

        Map<String, Object> fileResult = new HashMap<>();
        fileResult.put("originalName", originalFilename);
        fileResult.put("url", fileUrl);
        fileResult.put("type", fileType);
        fileResult.put("size", savedFile.length());
        fileResult.put("qualityInfo", qualityInfo);

        log.debug("event=submission_upload_file_completed userId={} mediaType={}", userId, fileType);
        return fileResult;
    }



    private Map<String, Object> generateFileSummary(List<Map<String, Object>> files) {
        Map<String, Object> summary = new HashMap<>();
        Map<String, Integer> typeCount = new HashMap<>();
        long totalSize = 0;

        typeCount.put("audio", 0);
        typeCount.put("video", 0);
        typeCount.put("lyric", 0);
        typeCount.put("image", 0);
        typeCount.put("other", 0);

        for (Map<String, Object> file : files) {
            String type = (String) file.get("type");
            typeCount.put(type, typeCount.get(type) + 1);
            totalSize += ((Number) file.get("size")).longValue();
        }

        summary.put("typeCount", typeCount);
        summary.put("totalSize", totalSize);
        summary.put("totalFiles", files.size());

        return summary;
    }
}
