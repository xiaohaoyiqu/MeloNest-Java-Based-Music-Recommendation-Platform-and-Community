package com.haoran.music.vo.admin;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;






@Data
public class MediaReplicaReconciliationVO {
    private LocalDateTime checkedAt;
    private boolean dryRun = true;
    private boolean databaseComplete;
    private boolean nodeInventoryAvailable;
    private boolean nodeInventoryComplete;
    private boolean missingConclusive;
    private boolean extraConclusive;
    private int expectedCount;
    private int actualCount;
    private int matchedCount;
    private int hashCheckedCount;
    private int hashUnavailableCount;
    private int hashSkippedCount;
    private List<Mismatch> missing = new ArrayList<>();
    private List<Mismatch> extra = new ArrayList<>();
    private List<Mismatch> sizeMismatch = new ArrayList<>();
    private List<Mismatch> hashMismatch = new ArrayList<>();
    private List<Mismatch> duplicateDatabasePaths = new ArrayList<>();
    private Map<String, Object> diskUsage;
    private Map<String, Object> queueObservation;
    private List<String> warnings = new ArrayList<>();





    @Data
    public static class Mismatch {
        private String type;
        private Long assetId;
        private String relativePath;
        private Long expectedSize;
        private Long actualSize;
        private String expectedHashPrefix;
        private String actualHashPrefix;
    }
}
