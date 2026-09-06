   
                      
   
package com.haoran.music.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haoran.music.entity.UserMusicReportSnapshot;
import com.haoran.music.mapper.UserMusicReportSnapshotMapper;
import com.haoran.music.service.UserMusicReportSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Slf4j
@Service
public class UserMusicReportSnapshotServiceImpl implements UserMusicReportSnapshotService {

    private static final long MAX_STALE_GRACE_MINUTES = 7L * 24L * 60L;
    private static final long MAX_FAILURE_BACKOFF_SECONDS = 10L * 60L;
    private static final int MAX_FAILURE_BACKOFF_KEYS = 4096;

    @Resource
    private UserMusicReportSnapshotMapper snapshotMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Resource(name = "recommendExecutor")
    private Executor refreshExecutor;

    @Value("${music.report-snapshot.stale-grace-minutes:1440}")
    private long staleGraceMinutes = 1440L;

    @Value("${music.report-snapshot.refresh-failure-backoff-seconds:60}")
    private long refreshFailureBackoffSeconds = 60L;

    private final Map<String, CompletableFuture<Map<String, Object>>> buildFlights = new ConcurrentHashMap<>();
    private final Map<String, Long> refreshFailureAt = new ConcurrentHashMap<>();
    private final LongAdder freshHits = new LongAdder();
    private final LongAdder staleServes = new LongAdder();
    private final LongAdder generatedCount = new LongAdder();
    private final LongAdder generationDurationMs = new LongAdder();
    private final LongAdder refreshFailures = new LongAdder();

    @Override
    public Map<String, Object> getOrCreate(Long userId, String reportType, String periodKey,
                                           LocalDateTime dataUntil, String calculationVersion, long ttlMinutes,
                                           Supplier<Map<String, Object>> supplier) {
        requireKey(userId, reportType, periodKey, calculationVersion, supplier);
        String flightKey = flightKey(userId, reportType, periodKey, calculationVersion);
        LocalDateTime now = LocalDateTime.now();
        UserMusicReportSnapshot latest = loadLatest(userId, reportType, periodKey);
        Map<String, Object> content = readVersionedContent(latest, calculationVersion);
        if (content != null && isFresh(latest, now)) {
            freshHits.increment();
            return decorate(content, latest, now, "FRESH", false);
        }
        if (content != null && isWithinStaleGrace(latest, now)) {
            staleServes.increment();
            String refreshStatus = triggerRefresh(flightKey, userId, reportType, periodKey,
                    dataUntil, calculationVersion, ttlMinutes, supplier);
            return decorate(content, latest, now, refreshStatus, true);
        }
        return buildSynchronously(flightKey, userId, reportType, periodKey,
                dataUntil, calculationVersion, ttlMinutes, supplier);
    }

    @Override
    public int invalidateSnapshot(Long userId, String reportType, String periodKey) {
        if (userId == null || reportType == null || reportType.trim().isEmpty()
                || periodKey == null || periodKey.trim().isEmpty()) {
            return 0;
        }
        try {
            return snapshotMapper.markSnapshotDeleted(userId, reportType, periodKey);
        } catch (Exception e) {
            log.warn("失效用户音乐报告快照失败: userId={}, reportType={}, periodKey={}, errorType={}",
                    userId, reportType, periodKey, e.getClass().getSimpleName());
            return 0;
        }
    }

    @Override
    public Map<String, Object> getSnapshotStatus() {
        pruneFailureBackoff();
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = snapshotMapper.selectSnapshotStatus();
            result.put("items", rows == null ? Collections.emptyList() : rows);
            result.put("databaseStatusAvailable", true);
        } catch (Exception e) {
            log.warn("读取用户音乐报告快照状态失败: errorType={}", e.getClass().getSimpleName());
            result.put("items", Collections.emptyList());
            result.put("databaseStatusAvailable", false);
        }
        long generationTotal = generatedCount.sum();
        result.put("inFlightKeys", buildFlights.size());
        result.put("refreshBackoffKeys", refreshFailureAt.size());
        result.put("staleGraceMinutes", safeStaleGraceMinutes());
        result.put("refreshFailureBackoffSeconds", safeFailureBackoffSeconds());
        result.put("freshHitsSinceStart", freshHits.sum());
        result.put("staleServesSinceStart", staleServes.sum());
        result.put("generatedSinceStart", generationTotal);
        result.put("refreshFailuresSinceStart", refreshFailures.sum());
        result.put("avgGenerationDurationMsSinceStart",
                generationTotal == 0L ? 0L : generationDurationMs.sum() / generationTotal);
        result.put("coverageMetadataPath", "snapshot.coverage");
        return result;
    }

    private Map<String, Object> buildSynchronously(String flightKey, Long userId, String reportType,
                                                    String periodKey, LocalDateTime dataUntil,
                                                    String calculationVersion, long ttlMinutes,
                                                    Supplier<Map<String, Object>> supplier) {
        CompletableFuture<Map<String, Object>> mine = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> existing = buildFlights.putIfAbsent(flightKey, mine);
        if (existing != null) {
            return await(existing);
        }
        try {
            UserMusicReportSnapshot rechecked = loadLatest(userId, reportType, periodKey);
            Map<String, Object> recheckedContent = readVersionedContent(rechecked, calculationVersion);
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> result;
            if (recheckedContent != null && isFresh(rechecked, now)) {
                freshHits.increment();
                result = decorate(recheckedContent, rechecked, now, "FRESH_AFTER_WAIT", false);
            } else {
                result = buildAndPersist(userId, reportType, periodKey, dataUntil,
                        calculationVersion, ttlMinutes, supplier);
            }
            mine.complete(result);
            return result;
        } catch (Throwable e) {
            mine.completeExceptionally(e);
            throw propagate(e);
        } finally {
            buildFlights.remove(flightKey, mine);
        }
    }

    private String triggerRefresh(String flightKey, Long userId, String reportType, String periodKey,
                                  LocalDateTime dataUntil, String calculationVersion, long ttlMinutes,
                                  Supplier<Map<String, Object>> supplier) {
        Long lastFailure = refreshFailureAt.get(flightKey);
        long backoffMillis = safeFailureBackoffSeconds() * 1000L;
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < backoffMillis) {
            return "STALE_REFRESH_BACKOFF";
        }

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        if (buildFlights.putIfAbsent(flightKey, future) != null) {
            return "STALE_REFRESH_IN_PROGRESS";
        }
        try {
            refreshExecutor.execute(() -> {
                try {
                    Map<String, Object> refreshed = buildAndPersist(userId, reportType, periodKey, dataUntil,
                            calculationVersion, ttlMinutes, supplier);
                    refreshFailureAt.remove(flightKey);
                    future.complete(refreshed);
                } catch (Throwable e) {
                    recordRefreshFailure(flightKey);
                    future.completeExceptionally(e);
                    log.warn("后台刷新用户音乐报告快照失败: userId={}, reportType={}, periodKey={}, errorType={}",
                            userId, reportType, periodKey, e.getClass().getSimpleName());
                } finally {
                    buildFlights.remove(flightKey, future);
                }
            });
            return "STALE_REFRESH_SCHEDULED";
        } catch (RuntimeException e) {
            buildFlights.remove(flightKey, future);
            recordRefreshFailure(flightKey);
            future.completeExceptionally(e);
            log.warn("后台刷新用户音乐报告快照提交失败: userId={}, reportType={}, periodKey={}, errorType={}",
                    userId, reportType, periodKey, e.getClass().getSimpleName());
            return "STALE_REFRESH_REJECTED";
        }
    }

    private Map<String, Object> buildAndPersist(Long userId, String reportType, String periodKey,
                                                 LocalDateTime dataUntil, String calculationVersion,
                                                 long ttlMinutes, Supplier<Map<String, Object>> supplier) {
        long startedNanos = System.nanoTime();
        Map<String, Object> supplied = supplier.get();
        if (supplied == null) {
            throw new IllegalStateException("报告生成结果不能为空");
        }
        long durationMs = Math.max(0L, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime expireTime = ttlMinutes > 0L ? generatedAt.plusMinutes(ttlMinutes) : null;
        Map<String, Object> content = new HashMap<>(supplied);
        appendStoredMetadata(content, generatedAt, dataUntil, calculationVersion, expireTime, durationMs);

        UserMusicReportSnapshot snapshot = new UserMusicReportSnapshot();
        snapshot.setUserId(userId);
        snapshot.setReportType(reportType);
        snapshot.setPeriodKey(periodKey);
        snapshot.setGeneratedAt(generatedAt);
        snapshot.setDataUntil(dataUntil);
        snapshot.setCalculationVersion(calculationVersion);
        snapshot.setExpireTime(expireTime);
        boolean persisted = persist(snapshot, content);

        generatedCount.increment();
        generationDurationMs.add(durationMs);
        return decorate(content, snapshot, generatedAt,
                persisted ? "GENERATED" : "GENERATED_NOT_PERSISTED", false);
    }

    private boolean persist(UserMusicReportSnapshot snapshot, Map<String, Object> content) {
        try {
            snapshot.setContentJson(objectMapper.writeValueAsString(content));
            return snapshotMapper.upsertSnapshot(snapshot) > 0;
        } catch (Exception e) {
            log.warn("写入用户音乐报告快照失败，继续返回实时结果: userId={}, reportType={}, periodKey={}, errorType={}",
                    snapshot.getUserId(), snapshot.getReportType(), snapshot.getPeriodKey(),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    private UserMusicReportSnapshot loadLatest(Long userId, String reportType, String periodKey) {
        try {
            return snapshotMapper.selectLatestSnapshot(userId, reportType, periodKey);
        } catch (Exception e) {
            log.warn("读取用户音乐报告快照失败，回退实时计算: userId={}, reportType={}, periodKey={}, errorType={}",
                    userId, reportType, periodKey, e.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> readVersionedContent(UserMusicReportSnapshot snapshot, String calculationVersion) {
        if (snapshot == null || !Objects.equals(calculationVersion, snapshot.getCalculationVersion())
                || snapshot.getGeneratedAt() == null || snapshot.getContentJson() == null
                || snapshot.getContentJson().trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(snapshot.getContentJson(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析用户音乐报告快照失败: reportType={}, periodKey={}, errorType={}",
                    snapshot.getReportType(), snapshot.getPeriodKey(), e.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> decorate(Map<String, Object> source, UserMusicReportSnapshot snapshot,
                                         LocalDateTime now, String status, boolean stale) {
        Map<String, Object> content = new HashMap<>(source);
        LocalDateTime generatedAt = snapshot.getGeneratedAt();
        LocalDateTime dataUntil = snapshot.getDataUntil();
        content.put("generatedAt", generatedAt);
        content.put("dataUntil", dataUntil);
        content.put("calculationVersion", snapshot.getCalculationVersion());

        Map<String, Object> previousMetadata = mapValue(source.get("snapshot"));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedAt", generatedAt);
        metadata.put("dataUntil", dataUntil);
        metadata.put("calculationVersion", snapshot.getCalculationVersion());
        metadata.put("expireTime", snapshot.getExpireTime());
        metadata.put("status", status);
        metadata.put("stale", stale);
        metadata.put("ageSeconds", secondsBetween(generatedAt, now));
        metadata.put("dataLagSeconds", secondsBetween(dataUntil, now));
        metadata.put("generationDurationMs", previousMetadata.get("generationDurationMs"));
        Object coverage = previousMetadata.get("coverage");
        metadata.put("coverage", coverage == null ? extractCoverage(source) : coverage);
        content.put("snapshot", metadata);
        return content;
    }

    private void appendStoredMetadata(Map<String, Object> content, LocalDateTime generatedAt,
                                      LocalDateTime dataUntil, String calculationVersion,
                                      LocalDateTime expireTime, long durationMs) {
        content.put("generatedAt", generatedAt);
        content.put("dataUntil", dataUntil);
        content.put("calculationVersion", calculationVersion);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedAt", generatedAt);
        metadata.put("dataUntil", dataUntil);
        metadata.put("calculationVersion", calculationVersion);
        metadata.put("expireTime", expireTime);
        metadata.put("generationDurationMs", durationMs);
        metadata.put("coverage", extractCoverage(content));
        content.put("snapshot", metadata);
    }

    private Map<String, Object> extractCoverage(Map<String, Object> content) {
        Map<String, Object> coverage = new HashMap<>();
        Object featureCoverage = content.get("featureCoverage");
        Object dataSource = content.get("dataSource");
        Map<String, Object> stats = mapValue(content.get("stats"));
        if (featureCoverage == null) {
            featureCoverage = stats.get("feature_coverage");
        }
        Map<String, Object> yearlyReport = mapValue(content.get("yearlyReport"));
        if (featureCoverage == null) {
            featureCoverage = yearlyReport.get("featureCoverage");
        }
        if (dataSource == null) {
            dataSource = yearlyReport.get("dataSource");
        }
        coverage.put("status", featureCoverage == null ? "NOT_REPORTED" : "REPORTED");
        coverage.put("featureRatio", featureCoverage);
        coverage.put("dataSource", dataSource);
        return coverage;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private boolean isFresh(UserMusicReportSnapshot snapshot, LocalDateTime now) {
        return snapshot.getExpireTime() == null || snapshot.getExpireTime().isAfter(now);
    }

    private boolean isWithinStaleGrace(UserMusicReportSnapshot snapshot, LocalDateTime now) {
        return snapshot.getExpireTime() != null && !snapshot.getExpireTime().isAfter(now)
                && !snapshot.getExpireTime().plusMinutes(safeStaleGraceMinutes()).isBefore(now);
    }

    private long secondsBetween(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null ? 0L : Math.max(0L, ChronoUnit.SECONDS.between(from, to));
    }

    private long safeStaleGraceMinutes() {
        return Math.max(0L, Math.min(staleGraceMinutes, MAX_STALE_GRACE_MINUTES));
    }

    private long safeFailureBackoffSeconds() {
        return Math.max(0L, Math.min(refreshFailureBackoffSeconds, MAX_FAILURE_BACKOFF_SECONDS));
    }

    private void recordRefreshFailure(String flightKey) {
        refreshFailures.increment();
        if (refreshFailureAt.size() >= MAX_FAILURE_BACKOFF_KEYS) {
            pruneFailureBackoff();
        }
        if (refreshFailureAt.size() < MAX_FAILURE_BACKOFF_KEYS) {
            refreshFailureAt.put(flightKey, System.currentTimeMillis());
        }
    }

    private void pruneFailureBackoff() {
        long cutoff = System.currentTimeMillis() - safeFailureBackoffSeconds() * 1000L;
        refreshFailureAt.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private Map<String, Object> await(CompletableFuture<Map<String, Object>> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            throw propagate(e.getCause() == null ? e : e.getCause());
        }
    }

    private RuntimeException propagate(Throwable e) {
        return e instanceof RuntimeException ? (RuntimeException) e : new IllegalStateException(e);
    }

    private String flightKey(Long userId, String reportType, String periodKey, String calculationVersion) {
        return userId + "\u001f" + reportType + "\u001f" + periodKey + "\u001f" + calculationVersion;
    }

    private void requireKey(Long userId, String reportType, String periodKey, String calculationVersion,
                            Supplier<Map<String, Object>> supplier) {
        if (userId == null || reportType == null || reportType.trim().isEmpty()
                || periodKey == null || periodKey.trim().isEmpty()
                || calculationVersion == null || calculationVersion.trim().isEmpty() || supplier == null) {
            throw new IllegalArgumentException("报告快照键、版本和生成器不能为空");
        }
    }
}
