


package com.haoran.music.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;




@Service
public class ApiPerformanceMetricsService {

    private final ConcurrentHashMap<String, EndpointMetrics> endpointMetrics = new ConcurrentHashMap<>();
    private final AtomicLong droppedEndpointRecords = new AtomicLong(0);
    private final LocalDateTime startedAt = LocalDateTime.now();

    @Value("${haoran.api.metrics.max-endpoints:200}")
    private int maxEndpoints;

    public void record(String module,
                       String className,
                       String methodName,
                       long durationMs,
                       long slowThresholdMs,
                       boolean success,
                       String errorMessage) {
        String key = buildKey(module, className, methodName);
        EndpointMetrics metrics = endpointMetrics.get(key);
        if (metrics == null) {
            if (endpointMetrics.size() >= resolveMaxEndpoints()) {
                droppedEndpointRecords.incrementAndGet();
                return;
            }
            EndpointMetrics created = new EndpointMetrics(module, className, methodName);
            EndpointMetrics raced = endpointMetrics.putIfAbsent(key, created);
            metrics = raced == null ? created : raced;
        }
        metrics.record(durationMs, slowThresholdMs, success, errorMessage);
    }

    public Map<String, Object> getStatus(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<Map<String, Object>> endpoints = endpointMetrics.values().stream()
                .map(EndpointMetrics::snapshot)
                .sorted(Comparator.comparingLong(this::readMaxDuration).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());

        long totalRequests = 0L;
        long totalSlowRequests = 0L;
        long totalFailures = 0L;
        long maxDuration = 0L;
        for (EndpointMetrics metrics : endpointMetrics.values()) {
            totalRequests += metrics.totalCount.get();
            totalSlowRequests += metrics.slowCount.get();
            totalFailures += metrics.failureCount.get();
            maxDuration = Math.max(maxDuration, metrics.maxDurationMs.get());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("startedAt", startedAt);
        result.put("trackedEndpointCount", endpointMetrics.size());
        result.put("maxEndpoints", resolveMaxEndpoints());
        result.put("droppedEndpointRecords", droppedEndpointRecords.get());
        result.put("totalRequests", totalRequests);
        result.put("totalSlowRequests", totalSlowRequests);
        result.put("totalFailures", totalFailures);
        result.put("maxDurationMs", maxDuration);
        result.put("topEndpoints", endpoints);
        return result;
    }

    private String buildKey(String module, String className, String methodName) {
        return safe(module) + "|" + safe(className) + "#" + safe(methodName);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int resolveMaxEndpoints() {
        return maxEndpoints <= 0 ? 200 : Math.min(maxEndpoints, 1000);
    }

    private long readMaxDuration(Map<String, Object> snapshot) {
        Object value = snapshot.get("maxDurationMs");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static class EndpointMetrics {
        private final String module;
        private final String className;
        private final String methodName;
        private final AtomicLong totalCount = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failureCount = new AtomicLong(0);
        private final AtomicLong slowCount = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        private final AtomicLong lastDurationMs = new AtomicLong(0);
        private final AtomicLong lastSeenEpochMs = new AtomicLong(0);
        private final AtomicReference<String> lastError = new AtomicReference<>();

        private EndpointMetrics(String module, String className, String methodName) {
            this.module = module;
            this.className = className;
            this.methodName = methodName;
        }

        private void record(long durationMs, long slowThresholdMs, boolean success, String errorMessage) {
            long safeDuration = Math.max(durationMs, 0L);
            totalCount.incrementAndGet();
            totalDurationMs.addAndGet(safeDuration);
            lastDurationMs.set(safeDuration);
            lastSeenEpochMs.set(System.currentTimeMillis());
            updateMax(maxDurationMs, safeDuration);
            if (safeDuration >= slowThresholdMs) {
                slowCount.incrementAndGet();
            }
            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
                lastError.set(truncate(errorMessage));
            }
        }

        private Map<String, Object> snapshot() {
            long total = totalCount.get();
            Map<String, Object> result = new HashMap<>();
            result.put("module", module);
            result.put("className", className);
            result.put("methodName", methodName);
            result.put("endpoint", className + "." + methodName);
            result.put("totalCount", total);
            result.put("successCount", successCount.get());
            result.put("failureCount", failureCount.get());
            result.put("slowCount", slowCount.get());
            result.put("avgDurationMs", total == 0 ? 0 : totalDurationMs.get() / total);
            result.put("maxDurationMs", maxDurationMs.get());
            result.put("lastDurationMs", lastDurationMs.get());
            result.put("lastSeenEpochMs", lastSeenEpochMs.get());
            result.put("lastError", lastError.get());
            return result;
        }

        private void updateMax(AtomicLong currentMax, long value) {
            long current;
            do {
                current = currentMax.get();
                if (value <= current) {
                    return;
                }
            } while (!currentMax.compareAndSet(current, value));
        }

        private String truncate(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
        }
    }
}
