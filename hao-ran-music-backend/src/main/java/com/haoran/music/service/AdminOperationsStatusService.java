   
                      
   
package com.haoran.music.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.entity.CuratedCarouselItem;
import com.haoran.music.entity.HotEvent;
import com.haoran.music.entity.PushNotification;
import com.haoran.music.mapper.CuratedCarouselItemMapper;
import com.haoran.music.mapper.HotEventMapper;
import com.haoran.music.mapper.MediaDerivativeTaskMapper;
import com.haoran.music.mapper.MVMapper;
import com.haoran.music.mapper.PushNotificationMapper;
import com.haoran.music.mapper.SchemaMigrationLedgerMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserActivityMapper;
import com.haoran.music.mapper.UserStatisticsMapper;
import com.haoran.music.common.util.ExternalUrlGuard;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

   
                                                     
   
@Slf4j
@Service
public class AdminOperationsStatusService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    @Resource
    private ApiPerformanceMetricsService apiPerformanceMetricsService;

    @Resource
    private PlayEventDeadLetterService playEventDeadLetterService;

    @Resource
    private MediaDerivativeTaskMapper mediaDerivativeTaskMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private MVMapper mvMapper;

    @Resource
    private CuratedCarouselItemMapper curatedCarouselItemMapper;

    @Resource
    private PushNotificationMapper pushNotificationMapper;

    @Resource
    private HotEventMapper hotEventMapper;

    @Resource
    private SchemaMigrationLedgerMapper schemaMigrationLedgerMapper;

    @Resource
    private UserStatisticsMapper userStatisticsMapper;

    @Resource
    private UserActivityMapper userActivityMapper;

    @Resource
    private UserMusicSummaryService userMusicSummaryService;

    @Resource
    private UserMusicSummaryBackfillTaskService summaryBackfillTaskService;

    @Resource
    private MusicReportRefreshTaskService reportRefreshTaskService;

    @Value("${haoran.operations.link-check-timeout-ms:2000}")
    private int linkCheckTimeoutMs;

    public Map<String, Object> getUnifiedStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", LocalDateTime.now());
        result.put("apiPerformance", apiPerformanceMetricsService.getStatus(10));
        result.put("playEventDeadLetters", safeMap(playEventDeadLetterService::getStatus));
        result.put("mediaDerivativeTasks", getMediaDerivativeTaskStatus(10));
        result.put("playableResources", getPlayableResourceQuality(10));
        result.put("curatedExternalLinkInventory", getExternalLinkInventory());
        result.put("userStatisticsAndActivity", getUserStatisticsAndActivityStatus());
        result.put("migrationLedger", getMigrationStatus(10));
        result.put("recoveryEntrypoints", Arrays.asList(
                "GET /admin/play-events/dead-letters/status",
                "POST /admin/play-events/dead-letters/{id}/replay",
                "POST /admin/play-events/dead-letters/{id}/ignore",
                "POST /admin/play-events/dead-letters/{id}/resolve",
                "POST /admin/operations/external-links/check",
                "POST /admin/operations/migrations/register"
        ));
        return result;
    }

    public Map<String, Object> getMediaDerivativeTaskStatus(int limit) {
        int safeLimit = safeLimit(limit);
        Map<String, Object> result = new HashMap<>();
        result.put("statusSummary", mediaDerivativeTaskMapper.selectStatusSummary());
        result.put("recentRiskTasks", mediaDerivativeTaskMapper.selectRecentRiskTasks(safeLimit));
        return result;
    }

    public Map<String, Object> getPlayableResourceQuality(int limit) {
        int safeLimit = safeLimit(limit);
        Map<String, Object> result = new LinkedHashMap<>();

        Long songTotal = safeLong(songMapper.countPublicSongs());
        Long songPlayable = safeLong(songMapper.countPublicPlayableSongs());
        Long songMissing = safeLong(songMapper.countPublicSongsMissingPlayableUrl());
        Map<String, Object> songs = new HashMap<>();
        songs.put("publicTotal", songTotal);
        songs.put("playable", songPlayable);
        songs.put("missingPlayableUrl", songMissing);
        songs.put("playableRate", rate(songPlayable, songTotal));
        songs.put("missingSamples", songMapper.selectPublicSongsMissingPlayableUrl(safeLimit));

        Long mvTotal = safeLong(mvMapper.countPublicMvs());
        Long mvPlayable = safeLong(mvMapper.countPublicPlayableMvs());
        Long mvMissing = safeLong(mvMapper.countPublicMvsMissingPlayableUrl());
        Map<String, Object> mvs = new HashMap<>();
        mvs.put("publicTotal", mvTotal);
        mvs.put("playable", mvPlayable);
        mvs.put("missingPlayableUrl", mvMissing);
        mvs.put("playableRate", rate(mvPlayable, mvTotal));
        mvs.put("missingSamples", mvMapper.selectPublicMvsMissingPlayableUrl(safeLimit));

        result.put("songs", songs);
        result.put("mvs", mvs);
        result.put("note", "Public recommendation and ranking candidates should prefer resources with at least one playable URL.");
        return result;
    }

    public Map<String, Object> getMigrationStatus(int limit) {
        int safeLimit = safeLimit(limit);
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("available", true);
            result.put("summary", schemaMigrationLedgerMapper.selectLedgerSummary());
            result.put("recent", schemaMigrationLedgerMapper.selectRecent(safeLimit));
        } catch (Exception e) {
            result.put("available", false);
            result.put("error", "schema_migration_ledger unavailable: " + e.getMessage());
        }
        return result;
    }

    public Map<String, Object> registerMigration(String scriptName,
                                                 String checksum,
                                                 String note,
                                                 Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        String safeScriptName = normalizeRequired(scriptName, 255, "脚本名称不能为空");
        String safeChecksum = normalizeOptional(checksum, 128);
        String safeNote = normalizeOptional(note, 1000);
        schemaMigrationLedgerMapper.upsert(safeScriptName, safeChecksum, "admin:" + operatorId, safeNote);

        Map<String, Object> result = new HashMap<>();
        result.put("scriptName", safeScriptName);
        result.put("migrationStatus", getMigrationStatus(10));
        return result;
    }

    public Map<String, Object> checkExternalLinks(int limit) {
        int safeLimit = safeLimit(limit);
        List<LinkCandidate> candidates = collectLinkCandidates(safeLimit);
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Integer> summary = new HashMap<>();
        summary.put("checked", 0);
        summary.put("ok", 0);
        summary.put("failed", 0);
        summary.put("skippedInternal", 0);

        for (LinkCandidate candidate : candidates) {
            Map<String, Object> check = checkCandidate(candidate);
            results.add(check);
            increment(summary, "checked");
            String status = String.valueOf(check.get("status"));
            if ("OK".equals(status)) {
                increment(summary, "ok");
            } else if ("SKIPPED_INTERNAL".equals(status)) {
                increment(summary, "skippedInternal");
            } else {
                increment(summary, "failed");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("checkedAt", LocalDateTime.now());
        result.put("timeoutMs", safeTimeoutMs());
        result.put("summary", summary);
        result.put("results", results);
        return result;
    }

    private Map<String, Object> getExternalLinkInventory() {
        Map<String, Object> result = new HashMap<>();
        result.put("activeApprovedCarouselItems", curatedCarouselItemMapper.selectCount(activeCarouselWrapper()));
        result.put("activeApprovedNews", pushNotificationMapper.selectCount(activeNewsWrapper()));
        result.put("activeApprovedFeaturedEvents", hotEventMapper.selectCount(activeFeaturedEventWrapper()));
        result.put("note", "Inventory is cheap; network health check is manual and bounded.");
        return result;
    }

    private Map<String, Object> getUserStatisticsAndActivityStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recentStatistics30Days", userStatisticsMapper.selectRecentStatus(LocalDate.now().minusDays(30)));
        result.put("recentActivities7Days", userActivityMapper.selectRecentStatus(LocalDateTime.now().minusDays(7)));
        result.put("musicSummary", safeMap(userMusicSummaryService::getSummaryStatus));
        result.put("summaryBackfillTasks", safeMap(summaryBackfillTaskService::getTaskStatus));
        result.put("reportRefreshTasks", safeMap(reportRefreshTaskService::getTaskStatus));
        return result;
    }

    private List<LinkCandidate> collectLinkCandidates(int limit) {
        List<LinkCandidate> candidates = new ArrayList<>();

        List<CuratedCarouselItem> carouselItems = curatedCarouselItemMapper.selectList(
                activeCarouselWrapper().orderByDesc(CuratedCarouselItem::getPriority)
                        .orderByAsc(CuratedCarouselItem::getSortOrder)
                        .orderByDesc(CuratedCarouselItem::getUpdateTime)
                        .last("LIMIT " + limit));
        for (CuratedCarouselItem item : carouselItems) {
            addCandidate(candidates, "curated_carousel_item", item.getId(), "imageUrl", item.getImageUrl());
            addCandidate(candidates, "curated_carousel_item", item.getId(), "fallbackImageUrl", item.getFallbackImageUrl());
            addCandidate(candidates, "curated_carousel_item", item.getId(), "link", item.getLink());
            addCandidate(candidates, "curated_carousel_item", item.getId(), "fallbackLink", item.getFallbackLink());
        }

        List<PushNotification> news = pushNotificationMapper.selectList(
                activeNewsWrapper().orderByDesc(PushNotification::getPriority)
                        .orderByDesc(PushNotification::getUpdateTime)
                        .last("LIMIT " + limit));
        for (PushNotification item : news) {
            addCandidate(candidates, "push_notification", item.getId(), "coverUrl", item.getCoverUrl());
            addCandidate(candidates, "push_notification", item.getId(), "link", item.getLink());
            addCandidate(candidates, "push_notification", item.getId(), "fallbackLink", item.getFallbackLink());
        }

        List<HotEvent> events = hotEventMapper.selectList(
                activeFeaturedEventWrapper().orderByAsc(HotEvent::getSortOrder)
                        .orderByDesc(HotEvent::getCreateTime)
                        .last("LIMIT " + limit));
        for (HotEvent item : events) {
            addCandidate(candidates, "hot_event", item.getId(), "cover", item.getCover());
            addCandidate(candidates, "hot_event", item.getId(), "sourceUrl", item.getSourceUrl());
            addCandidate(candidates, "hot_event", item.getId(), "fallbackSourceUrl", item.getFallbackSourceUrl());
        }

        return candidates;
    }

    private LambdaQueryWrapper<CuratedCarouselItem> activeCarouselWrapper() {
        LambdaQueryWrapper<CuratedCarouselItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CuratedCarouselItem::getDeleted, CommonConstants.NOT_DELETED)
                .eq(CuratedCarouselItem::getStatus, CommonConstants.STATUS_NORMAL)
                .and(w -> w.eq(CuratedCarouselItem::getReviewStatus, CommonConstants.STATUS_NORMAL)
                        .or()
                        .isNull(CuratedCarouselItem::getReviewStatus));
        return wrapper;
    }

    private LambdaQueryWrapper<PushNotification> activeNewsWrapper() {
        LambdaQueryWrapper<PushNotification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PushNotification::getDeleted, CommonConstants.NOT_DELETED)
                .eq(PushNotification::getStatus, CommonConstants.STATUS_NORMAL)
                .and(w -> w.eq(PushNotification::getReviewStatus, CommonConstants.STATUS_NORMAL)
                        .or()
                        .isNull(PushNotification::getReviewStatus));
        return wrapper;
    }

    private LambdaQueryWrapper<HotEvent> activeFeaturedEventWrapper() {
        LambdaQueryWrapper<HotEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HotEvent::getIsFeatured, true)
                .and(w -> w.eq(HotEvent::getIsDeleted, false)
                        .or()
                        .isNull(HotEvent::getIsDeleted))
                .and(w -> w.eq(HotEvent::getReviewStatus, CommonConstants.STATUS_NORMAL)
                        .or()
                        .isNull(HotEvent::getReviewStatus));
        return wrapper;
    }

    private void addCandidate(List<LinkCandidate> candidates,
                              String ownerType,
                              Long ownerId,
                              String fieldName,
                              String url) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        candidates.add(new LinkCandidate(ownerType, ownerId, fieldName, url.trim()));
    }

    private Map<String, Object> checkCandidate(LinkCandidate candidate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ownerType", candidate.ownerType);
        result.put("ownerId", candidate.ownerId);
        result.put("fieldName", candidate.fieldName);
        result.put("url", candidate.url);

        if (!isHttpUrl(candidate.url)) {
            result.put("status", "SKIPPED_INTERNAL");
            result.put("reason", "internal-or-relative-url");
            return result;
        }
        ExternalUrlGuard.Validation validation = ExternalUrlGuard.validate(candidate.url);
        if (!validation.isAllowed()) {
            result.put("status", "SKIPPED_UNSAFE");
            result.put("reason", validation.getReason());
            return result;
        }

        HttpCheckResult check = checkHttp(candidate.url, "HEAD");
        if (!check.ok && (check.statusCode == 403 || check.statusCode == 405)) {
            check = checkHttp(candidate.url, "GET");
        }
        result.put("status", check.ok ? "OK" : "FAILED");
        result.put("httpStatus", check.statusCode);
        result.put("reason", check.reason);
        return result;
    }

    private HttpCheckResult checkHttp(String urlValue, String method) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlValue);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(safeTimeoutMs());
            connection.setReadTimeout(safeTimeoutMs());
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", "HaoRanMusic-LinkChecker/1.0");
            if ("GET".equals(method)) {
                connection.setRequestProperty("Range", "bytes=0-0");
            }
            int code = connection.getResponseCode();
            return new HttpCheckResult(code >= 200 && code < 400, code, method);
        } catch (Exception e) {
            return new HttpCheckResult(false, -1, e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isHttpUrl(String value) {
        String lower = value == null ? "" : value.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private int safeLimit(int limit) {
        int value = limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(value, MAX_LIMIT);
    }

    private int safeTimeoutMs() {
        if (linkCheckTimeoutMs <= 0) {
            return 2000;
        }
        return Math.min(linkCheckTimeoutMs, 5000);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 10000D / denominator)) / 100D;
    }

    private void increment(Map<String, Integer> summary, String key) {
        summary.put(key, summary.getOrDefault(key, 0) + 1);
    }

    private String normalizeRequired(String value, int maxLength, String errorMessage) {
        String normalized = normalizeOptional(value, maxLength);
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private Map<String, Object> safeMap(StatusSupplier supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("available", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    private interface StatusSupplier {
        Map<String, Object> get();
    }

    private static class LinkCandidate {
        private final String ownerType;
        private final Long ownerId;
        private final String fieldName;
        private final String url;

        private LinkCandidate(String ownerType, Long ownerId, String fieldName, String url) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
            this.fieldName = fieldName;
            this.url = url;
        }
    }

    private static class HttpCheckResult {
        private final boolean ok;
        private final int statusCode;
        private final String reason;

        private HttpCheckResult(boolean ok, int statusCode, String reason) {
            this.ok = ok;
            this.statusCode = statusCode;
            this.reason = reason;
        }
    }
}
