


package com.haoran.music.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

public interface UserMusicReportSnapshotService {

    Map<String, Object> getOrCreate(Long userId,
                                    String reportType,
                                    String periodKey,
                                    LocalDateTime dataUntil,
                                    String calculationVersion,
                                    long ttlMinutes,
                                    Supplier<Map<String, Object>> supplier);

    int invalidateSnapshot(Long userId, String reportType, String periodKey);

    Map<String, Object> getSnapshotStatus();
}
