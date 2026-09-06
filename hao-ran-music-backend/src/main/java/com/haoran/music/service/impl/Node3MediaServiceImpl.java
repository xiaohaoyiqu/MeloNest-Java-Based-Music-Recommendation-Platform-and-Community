package com.haoran.music.service.impl;

import com.haoran.music.common.config.PostMediaConfig;
import com.haoran.music.common.util.CommonUtil;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.VideoCompressUtil;
import com.haoran.music.common.util.WorkProcessingUtil;
import com.haoran.music.service.Node3MediaService;
import com.haoran.music.service.model.Node3DiskUsage;
import com.haoran.music.service.model.Node3MediaInventory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

   
                                                            
  
                      
   
@Slf4j
@Service
public class Node3MediaServiceImpl implements Node3MediaService {

    private static final String INVENTORY_END_MARKER = "__HAORAN_INVENTORY_END__";
    private static final int MAX_INVENTORY_FILES = 1000;
    private static final int MAX_INVENTORY_OUTPUT_CHARS = 6 * 1024 * 1024;

    private final PostMediaConfig postMediaConfig;

    @Value("${music.node3.host}")
    private String node3Host;

    @Value("${music.node3.user}")
    private String node3User;

    @Value("${music.node3.port:22}")
    private Integer node3Port;

    @Value("${music.node3.key-path:}")
    private String node3KeyPath;

    @Value("${music.node3.known-hosts-path:/home/hdfs/.ssh/known_hosts}")
    private String node3KnownHostsPath;

    @Value("${music.node3.exists-cache-ttl-seconds:10}")
    private Integer node3ExistsCacheTtlSeconds = 10;

    private final Map<String, ExistsCacheEntry> existsCache = new ConcurrentHashMap<>();

    public Node3MediaServiceImpl(PostMediaConfig postMediaConfig) {
        this.postMediaConfig = postMediaConfig;
    }

    @Override
    public boolean uploadFile(File localFile, String remoteDir) {
        return uploadFile(localFile, remoteDir, ObjectUtils.isNotEmpty(localFile) ? localFile.getName() : null);
    }

    @Override
    public boolean uploadFile(File localFile, String remoteDir, String remoteFileName) {
        if (ObjectUtils.isEmpty(localFile) || !localFile.exists() || !localFile.isFile()) {
            log.warn("event=node3_upload_source_invalid");
            return false;
        }
        if (!isRemotePathSafe(remoteDir)) {
            log.warn("event=node3_upload_remote_directory_rejected");
            return false;
        }
        String safeRemoteFileName = sanitizeRemoteFileName(remoteFileName);
        if (ObjectUtils.isEmpty(safeRemoteFileName)) {
            log.warn("event=node3_upload_file_name_rejected");
            return false;
        }

        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", remoteDir).success) {
            return false;
        }

        String remotePath = appendRemotePath(remoteDir, safeRemoteFileName);
        String temporaryPath = temporaryPath(remotePath, "upload");
        List<String> command = buildScpBaseCommand();
        command.add(localFile.getAbsolutePath());
        command.add(node3User + "@" + node3Host + ":" + temporaryPath);
        CommandResult result = executeCommand(command, postMediaConfig.getVideoSshTimeoutSeconds());
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, remotePath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.error("event=node3_upload_failed errorCategory={}", commandErrorCategory(result));
            return false;
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(remotePath);
        return true;
    }
    @Override
    public VideoCompressUtil.VideoInfo probeVideo(String remotePath) {
        if (!isRemotePathSafe(remotePath)) {
            return null;
        }

        CommandResult result = runRemoteCommand(
                Math.max(5, postMediaConfig.getVideoProbeTimeoutSeconds()),
                postMediaConfig.getVideoFfprobePath(),
                "-v", "error",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                remotePath
        );
        if (!result.success) {
            log.warn("event=node3_ffprobe_failed errorCategory={}", commandErrorCategory(result));
            return null;
        }

        int duration = parseIntFromDouble(result.output, "\"duration\"\\s*:\\s*\"?(\\d+\\.?\\d*)");
        int width = parseInt(result.output, "\"width\"\\s*:\\s*(\\d+)");
        int height = parseInt(result.output, "\"height\"\\s*:\\s*(\\d+)");
        long size = parseLong(result.output, "\"size\"\\s*:\\s*\"?(\\d+)");
        String format = CommonUtil.extractString(result.output, "\"format_name\"\\s*:\\s*\"([^\"]+)\"");

        return new VideoCompressUtil.VideoInfo(duration, width, height, size, format);
    }

    @Override
    public boolean generateVideoVariant(String inputPath, String outputPath, int targetHeight, int crf,
                                        int minDuration, int maxDuration) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath)) {
            return false;
        }

        VideoCompressUtil.VideoInfo info = probeVideo(inputPath);
        if (ObjectUtils.isEmpty(info)) {
            return false;
        }
        if (info.getDuration() < minDuration || info.getDuration() > maxDuration) {
            log.warn("event=node3_video_duration_rejected duration={}", info.getDuration());
            return false;
        }

        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }

        List<String> args = new ArrayList<>();
        args.add(postMediaConfig.getVideoFfmpegPath());
        args.add("-nostdin");
        args.add("-v");
        args.add("error");
        args.add("-y");
        args.add("-i");
        args.add(inputPath);
        if (targetHeight > 0 && info.getHeight() > targetHeight) {
            args.add("-vf");
            args.add("scale=-2:" + targetHeight);
        }
        args.add("-c:v");
        args.add("libx264");
        args.add("-preset");
        args.add("medium");
        args.add("-crf");
        args.add(String.valueOf(crf));
        args.add("-c:a");
        args.add("aac");
        args.add("-b:a");
        args.add("128k");
        args.add("-ac");
        args.add("2");
        args.add("-movflags");
        args.add("+faststart");
        String temporaryPath = temporaryPath(outputPath, "transcode");
        args.add(temporaryPath);

        CommandResult result = runRemoteCommand(postMediaConfig.getVideoTranscodeTimeoutSeconds(),
                args.toArray(new String[0]));
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.error("event=node3_video_variant_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }

    @Override
    public boolean generateAudioVariant(String inputPath, String outputPath, int bitrateKbps) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath) || bitrateKbps <= 0) {
            return false;
        }
        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }

        int safeBitrate = Math.min(Math.max(bitrateKbps, 64), 320);
        String temporaryPath = temporaryPath(outputPath, "transcode");
        CommandResult result = runRemoteCommand(
                postMediaConfig.getVideoTranscodeTimeoutSeconds(),
                postMediaConfig.getVideoFfmpegPath(),
                "-nostdin",
                "-v", "error",
                "-y",
                "-i", inputPath,
                "-vn",
                "-c:a", "libmp3lame",
                "-b:a", safeBitrate + "k",
                temporaryPath
        );
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.error("event=node3_audio_variant_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }

    @Override
    public boolean generateAudioPreview(String inputPath, String outputPath, int maxSeconds) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath)
                || maxSeconds <= 0 || maxSeconds > 60) {
            return false;
        }
        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }
        String temporaryPath = temporaryPath(outputPath, "preview");
        CommandResult result = runRemoteCommand(
                postMediaConfig.getVideoTranscodeTimeoutSeconds(),
                postMediaConfig.getVideoFfmpegPath(),
                "-nostdin", "-v", "error", "-y", "-i", inputPath,
                "-t", String.valueOf(maxSeconds), "-vn",
                "-c:a", "libmp3lame", "-b:a", "128k", temporaryPath);
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.warn("event=node3_audio_preview_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }

    @Override
    public boolean generateVideoPreview(String inputPath, String outputPath, int maxSeconds) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath)
                || maxSeconds <= 0 || maxSeconds > 60) {
            return false;
        }
        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }
        String temporaryPath = temporaryPath(outputPath, "preview");
        CommandResult result = runRemoteCommand(
                postMediaConfig.getVideoTranscodeTimeoutSeconds(),
                postMediaConfig.getVideoFfmpegPath(),
                "-nostdin", "-v", "error", "-y", "-i", inputPath,
                "-t", String.valueOf(maxSeconds),
                "-c:v", "libx264", "-preset", "medium", "-crf", "28",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", temporaryPath);
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.warn("event=node3_video_preview_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }
    @Override
    public boolean generateVideoThumbnail(String inputPath, String outputPath, int timeSeconds) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath)) {
            return false;
        }
        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }

        String temporaryPath = temporaryPath(outputPath, "thumbnail");
        CommandResult result = runRemoteCommand(
                Math.max(10, postMediaConfig.getVideoProbeTimeoutSeconds()),
                postMediaConfig.getVideoFfmpegPath(),
                "-nostdin",
                "-v", "error",
                "-y",
                "-ss", String.valueOf(timeSeconds),
                "-i", inputPath,
                "-vframes", "1",
                "-q:v", "2",
                temporaryPath
        );
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.warn("event=node3_thumbnail_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }

    @Override
    public boolean exists(String remotePath) {
        if (!isRemotePathSafe(remotePath)) {
            return false;
        }
        ExistsCacheEntry cached = existsCache.get(remotePath);
        if (cached != null) {
            if (cached.expiresAt > System.currentTimeMillis()) {
                return cached.exists;
            }
            existsCache.remove(remotePath, cached);
        }
        CommandResult result = runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(),
                "test", "-f", remotePath);
        existsCache.put(remotePath, new ExistsCacheEntry(
                result.success,
                System.currentTimeMillis() + cacheTtlMillis()
        ));
        return result.success;
    }

    @Override
    public boolean copyRemoteFile(String inputPath, String outputPath) {
        if (!isRemotePathSafe(inputPath) || !isRemotePathSafe(outputPath)) {
            return false;
        }
        String outputDir = parentDirectory(outputPath);
        if (!runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "mkdir", "-p", outputDir).success) {
            return false;
        }
        String temporaryPath = temporaryPath(outputPath, "copy");
        CommandResult result = runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(),
                "cp", "-f", "--", inputPath, temporaryPath);
        if (result.success) {
            result = promoteTemporaryFile(temporaryPath, outputPath);
        }
        if (!result.success) {
            deleteQuietly(temporaryPath);
            log.warn("event=node3_remote_copy_failed errorCategory={}", commandErrorCategory(result));
        }
        invalidateExistenceCache(temporaryPath);
        invalidateExistenceCache(outputPath);
        return result.success && exists(outputPath);
    }

    @Override
    public long fileSize(String remotePath) {
        if (!isRemotePathSafe(remotePath)) {
            return 0L;
        }
        CommandResult result = runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(),
                "stat", "-c", "%s", remotePath);
        if (!result.success || ObjectUtils.isEmpty(result.output)) {
            return 0L;
        }
        try {
            return Long.parseLong(result.output.trim().split("\\s+")[0]);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public Node3MediaInventory inventory(String remoteRoot, int limit) {
        String root = normalizeInventoryRoot(remoteRoot);
        if (root == null) {
            return new Node3MediaInventory(false, false, "INVALID_ROOT", new ArrayList<>());
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_INVENTORY_FILES));
        String command = "test -d '" + root + "' || exit 2; "
                + "find '" + root + "' -xdev -type f -printf '%p\\t%s\\0' "
                + "| head -z -n " + (safeLimit + 1) + "; "
                + "printf '" + INVENTORY_END_MARKER + "\\0'";
        CommandResult result = runRemoteShellCommand(
                Math.max(10, postMediaConfig.getVideoSshTimeoutSeconds()),
                MAX_INVENTORY_OUTPUT_CHARS,
                command);
        if (!result.success) {
            return new Node3MediaInventory(false, false, "REMOTE_INVENTORY_FAILED", new ArrayList<>());
        }
        String[] records = result.output.split("\u0000", -1);
        List<Node3MediaInventory.FileEntry> files = new ArrayList<>();
        boolean markerFound = false;
        for (String record : records) {
            if (INVENTORY_END_MARKER.equals(record)) {
                markerFound = true;
                break;
            }
            if (record == null || record.isEmpty()) {
                continue;
            }
            int separator = record.lastIndexOf('\t');
            if (separator <= 0 || separator == record.length() - 1) {
                return new Node3MediaInventory(false, false, "MALFORMED_INVENTORY", new ArrayList<>());
            }
            String path = record.substring(0, separator);
            if (!path.startsWith(root + "/") || !WorkProcessingUtil.isPathSafe(path)) {
                return new Node3MediaInventory(false, false, "OUT_OF_ROOT_INVENTORY", new ArrayList<>());
            }
            try {
                files.add(new Node3MediaInventory.FileEntry(
                        path, Long.parseLong(record.substring(separator + 1))));
            } catch (NumberFormatException e) {
                return new Node3MediaInventory(false, false, "MALFORMED_INVENTORY", new ArrayList<>());
            }
        }
        if (!markerFound) {
            return new Node3MediaInventory(false, false, "TRUNCATED_INVENTORY", new ArrayList<>());
        }
        boolean complete = files.size() <= safeLimit;
        if (!complete) {
            files = new ArrayList<>(files.subList(0, safeLimit));
        }
        return new Node3MediaInventory(true, complete, null, files);
    }

    @Override
    public Node3DiskUsage diskUsage(String remoteRoot) {
        String root = normalizeInventoryRoot(remoteRoot);
        if (root == null) {
            return new Node3DiskUsage(false, 0L, 0L, 0L, null, "INVALID_ROOT");
        }
        CommandResult result = runRemoteShellCommand(
                Math.max(5, postMediaConfig.getVideoSshTimeoutSeconds()),
                8192,
                "test -d '" + root + "' || exit 2; df -Pk '" + root + "' | tail -n 1");
        if (!result.success || ObjectUtils.isEmpty(result.output)) {
            return new Node3DiskUsage(false, 0L, 0L, 0L, null, "REMOTE_DISK_USAGE_FAILED");
        }
        String[] values = result.output.trim().split("\\s+");
        if (values.length < 5) {
            return new Node3DiskUsage(false, 0L, 0L, 0L, null, "MALFORMED_DISK_USAGE");
        }
        try {
            long total = Long.parseLong(values[1]) * 1024L;
            long used = Long.parseLong(values[2]) * 1024L;
            long available = Long.parseLong(values[3]) * 1024L;
            int percent = Integer.parseInt(values[4].replace("%", ""));
            return new Node3DiskUsage(true, total, used, available, percent, null);
        } catch (NumberFormatException e) {
            return new Node3DiskUsage(false, 0L, 0L, 0L, null, "MALFORMED_DISK_USAGE");
        }
    }

    @Override
    public String sha256(String remotePath) {
        if (!isStrictRemoteFilePath(remotePath)) {
            return null;
        }
        CommandResult result = runRemoteShellCommand(
                Math.max(10, Math.min(30, postMediaConfig.getVideoSshTimeoutSeconds())),
                4096,
                "sha256sum -- '" + remotePath + "'");
        if (!result.success || ObjectUtils.isEmpty(result.output)) {
            return null;
        }
        String hash = result.output.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return hash.matches("[a-f0-9]{64}") ? hash : null;
    }

    @Override
    public boolean deleteQuietly(String remotePath) {
        if (!isRemotePathSafe(remotePath)) {
            return false;
        }
        CommandResult result = runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(), "rm", "-f", remotePath);
        invalidateExistenceCache(remotePath);
        return result.success;
    }

    protected CommandResult runRemoteCommand(int timeoutSeconds, String... remoteArgs) {
        List<String> command = buildSshBaseCommand();
        for (String arg : remoteArgs) {
            command.add(arg);
        }
        return executeCommand(command, timeoutSeconds);
    }

       
                                  
      
                                 
                                    
                                 
                   
       
    protected CommandResult runRemoteShellCommand(int timeoutSeconds, int maxOutputChars, String shellCommand) {
        List<String> command = buildSshBaseCommand();
        command.add(shellCommand);
        return executeCommand(command, timeoutSeconds, maxOutputChars);
    }

    private List<String> buildSshBaseCommand() {
        List<String> command = new ArrayList<>();
        command.add("ssh");
        command.add("-o");
        command.add("BatchMode=yes");
        command.add("-o");
        command.add("StrictHostKeyChecking=yes");
        command.add("-o");
        command.add("UserKnownHostsFile=" + node3KnownHostsPath);
        command.add("-p");
        command.add(String.valueOf(node3Port));
        addKeyIfAvailable(command);
        command.add(node3User + "@" + node3Host);
        return command;
    }

    private List<String> buildScpBaseCommand() {
        List<String> command = new ArrayList<>();
        command.add("scp");
        command.add("-o");
        command.add("BatchMode=yes");
        command.add("-o");
        command.add("StrictHostKeyChecking=yes");
        command.add("-o");
        command.add("UserKnownHostsFile=" + node3KnownHostsPath);
        command.add("-P");
        command.add(String.valueOf(node3Port));
        addKeyIfAvailable(command);
        return command;
    }

    private void addKeyIfAvailable(List<String> command) {
        File keyFile = ObjectUtils.isNotEmpty(node3KeyPath) ? new File(node3KeyPath) : null;
        if (ObjectUtils.isNotEmpty(keyFile) && keyFile.exists() && keyFile.length() > 0) {
            command.add("-i");
            command.add(node3KeyPath);
        }
    }
    protected CommandResult executeCommand(List<String> command, int timeoutSeconds) {
        return executeCommand(command, timeoutSeconds, 64 * 1024);
    }

    private CommandResult executeCommand(List<String> command, int timeoutSeconds, int maxOutputChars) {
        try {
            com.haoran.music.common.util.ProcessExecutionUtil.Result result =
                    com.haoran.music.common.util.ProcessExecutionUtil.execute(
                            command, timeoutSeconds, maxOutputChars);
            if (result.isTimedOut()) {
                return new CommandResult(false, "command timeout");
            }
            return new CommandResult(result.getExitCode() == 0, result.getOutput());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(false, e.getMessage());
        } catch (Exception e) {
            return new CommandResult(false, e.getMessage());
        }
    }

    private boolean isRemotePathSafe(String path) {
        return ObjectUtils.isNotEmpty(path) && path.startsWith("/") && WorkProcessingUtil.isPathSafe(path);
    }

    private String normalizeInventoryRoot(String root) {
        if (ObjectUtils.isEmpty(root)) {
            return null;
        }
        String normalized = root.trim().replaceAll("/+$", "");
        if (!normalized.matches("/[A-Za-z0-9_./-]+") || normalized.contains("..")) {
            return null;
        }
        return normalized;
    }

    private boolean isStrictRemoteFilePath(String path) {
        return isRemotePathSafe(path) && path.matches("/[A-Za-z0-9_./-]+");
    }

    private String sanitizeRemoteFileName(String filename) {
        if (ObjectUtils.isEmpty(filename)) {
            return null;
        }
        String cleanName = new File(filename).getName()
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        if (ObjectUtils.isEmpty(cleanName) || cleanName.startsWith(".")) {
            return null;
        }
        return cleanName;
    }
    private String appendRemotePath(String remoteDir, String filename) {
        String normalizedDir = remoteDir.endsWith("/") ? remoteDir.substring(0, remoteDir.length() - 1) : remoteDir;
        return normalizedDir + "/" + filename;
    }

       
                                              
                              
       
    private String temporaryPath(String targetPath, String operation) {
        return targetPath + "." + operation + "-" + UUID.randomUUID();
    }

       
                                      
       
    private CommandResult promoteTemporaryFile(String temporaryPath, String targetPath) {
        return runRemoteCommand(postMediaConfig.getVideoSshTimeoutSeconds(),
                "mv", "-f", "--", temporaryPath, targetPath);
    }

    private String parentDirectory(String remotePath) {
        int separator = remotePath.lastIndexOf('/');
        return separator <= 0 ? "/" : remotePath.substring(0, separator);
    }

    private String safeFileName(File localFile) {
        return localFile == null ? null : sanitizeRemoteFileName(localFile.getName());
    }

       
                                   
       
    private String commandErrorCategory(CommandResult result) {
        if (result == null || ObjectUtils.isEmpty(result.output)) {
            return "COMMAND_FAILED";
        }
        return result.output.toLowerCase(Locale.ROOT).contains("timeout")
                ? "COMMAND_TIMEOUT" : "COMMAND_FAILED";
    }

    private int parseInt(String text, String regex) {
        Integer value = CommonUtil.extractInt(text, regex);
        return value == null ? 0 : value;
    }

    private int parseIntFromDouble(String text, String regex) {
        Double value = CommonUtil.extractDouble(text, regex);
        return value == null ? 0 : value.intValue();
    }

    private long parseLong(String text, String regex) {
        String value = CommonUtil.extractString(text, regex);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private long cacheTtlMillis() {
        return Math.max(1, node3ExistsCacheTtlSeconds == null ? 10 : node3ExistsCacheTtlSeconds) * 1000L;
    }

    void invalidateExistenceCache(String remotePath) {
        if (remotePath != null) {
            existsCache.remove(remotePath);
        }
    }

    static class CommandResult {
        final boolean success;
        final String output;

        CommandResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    private static class ExistsCacheEntry {
        private final boolean exists;
        private final long expiresAt;

        private ExistsCacheEntry(boolean exists, long expiresAt) {
            this.exists = exists;
            this.expiresAt = expiresAt;
        }
    }
}

