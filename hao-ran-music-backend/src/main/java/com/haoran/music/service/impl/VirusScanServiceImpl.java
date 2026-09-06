


package com.haoran.music.service.impl;

import com.haoran.music.common.context.UserContext;
import com.haoran.music.entity.VirusScanRecord;
import com.haoran.music.mapper.VirusScanRecordMapper;
import com.haoran.music.service.VirusScanService;
import fi.solita.clamav.ClamAVClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;






@Slf4j
@Service
@ConditionalOnProperty(prefix = "clamav", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VirusScanServiceImpl implements VirusScanService {

    private static final String TARGET_FILE = "file";
    private static final String TARGET_STREAM = "input_stream";
    private static final String TARGET_BYTES = "bytes";
    private static final String RESULT_CLEAN = "clean";
    private static final String RESULT_INFECTED = "infected";
    private static final String RESULT_ERROR = "error";
    private static final int MAX_TEXT_LENGTH = 1000;

    @Value("${clamav.host}")
    private String host;

    @Value("${clamav.port}")
    private int port;

    @Value("${clamav.timeout}")
    private int timeout;

    @Value("${clamav.fail-on-unavailable:true}")
    private boolean failOnUnavailable;

    @Value("${clamav.quarantine-enabled:true}")
    private boolean quarantineEnabled;

    @Value("${clamav.quarantine-path:/sdb1/myprojoct/haoranmusic/quarantine/}")
    private String quarantinePath;

    @Resource
    private VirusScanRecordMapper virusScanRecordMapper;

    private ClamAVClient client;

    @PostConstruct
    public void init() {
        try {
            client = new ClamAVClient(host, port, timeout);
            if (client.ping()) {
                log.info("event=virus_scanner_initialized");
            } else {
                log.warn("event=virus_scanner_initialization_unavailable");
                client = null;
            }
        } catch (Exception e) {
            log.warn("event=virus_scanner_initialization_failed errorType={}",
                    e.getClass().getSimpleName());
            client = null;
        }
    }

    @Override
    public boolean scanFile(File file) throws Exception {
        long start = System.currentTimeMillis();
        if (file == null || !file.exists()) {
            recordScan(TARGET_FILE, file, null, RESULT_ERROR, null, null, "file does not exist", start);
            throw new IllegalArgumentException("文件不存在");
        }

        if (!isAvailable()) {
            recordScan(TARGET_FILE, file, null, RESULT_ERROR, null, null, "ClamAV service unavailable", start);
            return handleUnavailable(file.getName());
        }

        log.debug("event=virus_scan_started targetType=file");
        try (FileInputStream fis = new FileInputStream(file)) {
            ScanOutcome outcome = scanInternal(fis, file.getName());
            String quarantine = null;
            if (!outcome.clean) {
                quarantine = quarantineFile(file);
            }
            recordScan(TARGET_FILE, file, file.length(), outcome.result(), outcome.threatName, quarantine, null, start);
            return outcome.clean;
        } catch (Exception e) {
            recordScan(TARGET_FILE, file, file.length(), RESULT_ERROR, null, null,
                    e.getClass().getSimpleName(), start);
            log.error("event=virus_scan_failed targetType=file errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public boolean scanInputStream(InputStream inputStream, String filename) throws Exception {
        long start = System.currentTimeMillis();
        if (inputStream == null) {
            recordScan(TARGET_STREAM, filename, null, RESULT_ERROR, null, null, "input stream is null", start);
            throw new IllegalArgumentException("输入流不能为空");
        }

        if (!isAvailable()) {
            recordScan(TARGET_STREAM, filename, null, RESULT_ERROR, null, null, "ClamAV service unavailable", start);
            return handleUnavailable(filename);
        }

        log.debug("event=virus_scan_started targetType=stream");
        try {
            ScanOutcome outcome = scanInternal(inputStream, filename);
            recordScan(TARGET_STREAM, filename, null, outcome.result(), outcome.threatName, null, null, start);
            return outcome.clean;
        } catch (Exception e) {
            recordScan(TARGET_STREAM, filename, null, RESULT_ERROR, null, null,
                    e.getClass().getSimpleName(), start);
            log.error("event=virus_scan_failed targetType=stream errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public boolean scanBytes(byte[] data, String filename) throws Exception {
        long start = System.currentTimeMillis();
        if (data == null || data.length == 0) {
            recordScan(TARGET_BYTES, filename, 0L, RESULT_ERROR, null, null, "empty bytes", start);
            throw new IllegalArgumentException("数据不能为空");
        }

        if (!isAvailable()) {
            recordScan(TARGET_BYTES, filename, (long) data.length, RESULT_ERROR, null, null, "ClamAV service unavailable", start);
            return handleUnavailable(filename);
        }

        log.debug("event=virus_scan_started targetType=bytes byteCount={}", data.length);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            ScanOutcome outcome = scanInternal(inputStream, filename);
            recordScan(TARGET_BYTES, filename, (long) data.length, outcome.result(), outcome.threatName, null, null, start);
            return outcome.clean;
        } catch (Exception e) {
            recordScan(TARGET_BYTES, filename, (long) data.length, RESULT_ERROR, null, null,
                    e.getClass().getSimpleName(), start);
            log.error("event=virus_scan_failed targetType=bytes errorType={}",
                    e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public boolean isAvailable() {
        if (client == null) {
            return false;
        }
        try {
            return client.ping();
        } catch (Exception e) {
            log.debug("event=virus_scanner_unavailable errorType={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public String getVersion() {
        if (!isAvailable()) {
            return "Unknown";
        }
        try {
            return client.ping() ? "Connected (fi.solita.clamav)" : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", true);
        status.put("available", isAvailable());
        status.put("version", getVersion());
        status.put("host", host);
        status.put("port", port);
        status.put("timeout", timeout);
        status.put("failOnUnavailable", failOnUnavailable);
        status.put("quarantineEnabled", quarantineEnabled);
        status.put("quarantinePath", quarantinePath);
        return status;
    }

    private ScanOutcome scanInternal(InputStream inputStream, String filename) throws Exception {
        byte[] result = client.scan(inputStream);
        boolean clean = ClamAVClient.isCleanReply(result);
        String response = new String(result, StandardCharsets.UTF_8).trim();
        if (clean) {
            log.debug("event=virus_scan_passed");
            return new ScanOutcome(true, null, response);
        }

        String threatName = parseThreatName(response);
        log.warn("event=virus_threat_detected");
        return new ScanOutcome(false, threatName, response);
    }

    private boolean handleUnavailable(String filename) {
        String name = filename == null ? "unknown" : filename;
        if (failOnUnavailable) {
            throw new IllegalStateException("ClamAV service unavailable: " + name);
        }
        log.warn("event=virus_scan_skipped scannerUnavailable=true");
        return true;
    }

    private String quarantineFile(File file) {
        if (!quarantineEnabled || file == null || !file.exists()) {
            return null;
        }

        try {
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String safeName = sanitizeFileName(file.getName());
            Path target = Paths.get(quarantinePath, date, System.currentTimeMillis() + "_" + safeName);
            Files.createDirectories(target.getParent());
            Files.move(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            log.warn("event=virus_file_quarantined");
            return target.toString();
        } catch (Exception e) {
            log.error("event=virus_file_quarantine_failed errorType={}",
                    e.getClass().getSimpleName());
            return null;
        }
    }

    private void recordScan(String target, File file, Long size, String result, String threatName,
                            String quarantine, String errorMessage, long startTime) {
        String fileName = file == null ? null : file.getName();
        String filePath = file == null ? null : file.getAbsolutePath();
        Long fileSize = size;
        if (fileSize == null && file != null && file.exists()) {
            fileSize = file.length();
        }
        recordScan(target, fileName, filePath, fileSize, result, threatName, quarantine, errorMessage, startTime);
    }

    private void recordScan(String target, String fileName, Long size, String result, String threatName,
                            String quarantine, String errorMessage, long startTime) {
        recordScan(target, fileName, null, size, result, threatName, quarantine, errorMessage, startTime);
    }

    private void recordScan(String target, String fileName, String filePath, Long size, String result, String threatName,
                            String quarantine, String errorMessage, long startTime) {
        try {
            VirusScanRecord record = new VirusScanRecord();
            record.setUserId(UserContext.getCurrentUserId());
            record.setClientIp(truncate(UserContext.getClientIp(), 64));
            record.setBusinessType(inferBusinessType(filePath, fileName));
            record.setScanTarget(target);
            record.setFileName(truncate(fileName, 255));
            record.setFilePath(truncate(filePath, MAX_TEXT_LENGTH));
            record.setFileSize(size);
            record.setResult(result);
            record.setThreatName(truncate(threatName, 255));
            record.setScannerHost(host);
            record.setScannerPort(port);
            record.setQuarantined(quarantine == null ? 0 : 1);
            record.setQuarantinePath(truncate(quarantine, MAX_TEXT_LENGTH));
            record.setErrorMessage(truncate(errorMessage, MAX_TEXT_LENGTH));
            record.setDurationMs(System.currentTimeMillis() - startTime);
            record.setCreateTime(LocalDateTime.now());
            if (virusScanRecordMapper.insert(record) != 1) {
                log.warn("event=virus_scan_audit_write_failed errorType=WRITE_NOT_APPLIED");
            }
        } catch (Exception e) {
            log.warn("event=virus_scan_audit_write_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }

    private String parseThreatName(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        int foundIndex = response.indexOf(" FOUND");
        if (foundIndex < 0) {
            return truncate(response, 255);
        }
        int colonIndex = response.indexOf(':');
        int start = colonIndex >= 0 ? colonIndex + 1 : 0;
        return truncate(response.substring(start, foundIndex).trim(), 255);
    }

    private String inferBusinessType(String filePath, String fileName) {
        String source = ((filePath == null ? "" : filePath) + "/" + (fileName == null ? "" : fileName)).toLowerCase();
        if (source.contains("payment")) {
            return "payment";
        }
        if (source.contains("emoji")) {
            return "emoji";
        }
        if (source.contains("post") || source.contains("video")) {
            return "post-media";
        }
        if (source.contains("song_request") || source.contains("song-requests")) {
            return "song-request";
        }
        if (source.contains("creator")) {
            return "creator-work";
        }
        if (source.contains("music-square")) {
            return "music-square";
        }
        return "upload";
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "unknown";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @PreDestroy
    public void destroy() {
        log.info("event=virus_scanner_closed");
    }

    private static class ScanOutcome {
        private final boolean clean;
        private final String threatName;
        private final String rawResponse;

        private ScanOutcome(boolean clean, String threatName, String rawResponse) {
            this.clean = clean;
            this.threatName = threatName;
            this.rawResponse = rawResponse;
        }

        private String result() {
            return clean ? RESULT_CLEAN : RESULT_INFECTED;
        }
    }
}
