


package com.haoran.music.service.impl;

import com.haoran.music.mapper.UserMusicTopItemSummaryMapper;
import com.haoran.music.service.UserMusicTopItemSummaryService;
import com.haoran.music.service.UserMusicSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserMusicTopItemSummaryServiceImpl implements UserMusicTopItemSummaryService {

    @Resource
    private UserMusicTopItemSummaryMapper topItemSummaryMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshDailyTopItems(LocalDate statDate) {
        LocalDate targetDate = statDate == null ? LocalDate.now().minusDays(1) : statDate;
        return refresh(PERIOD_DAILY, targetDate, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshMonthlyTopItems(YearMonth statMonth) {
        YearMonth targetMonth = statMonth == null ? YearMonth.now() : statMonth;
        LocalDate periodKey = targetMonth.atDay(1);
        return refresh(PERIOD_MONTHLY, periodKey, periodKey.atStartOfDay(),
                targetMonth.plusMonths(1).atDay(1).atStartOfDay());
    }

    private int refresh(String periodType, LocalDate periodKey, LocalDateTime startTime, LocalDateTime endTime) {
        topItemSummaryMapper.markPeriodDeleted(periodType, periodKey);
        int songs = topItemSummaryMapper.refreshSongSummary(
                periodType, periodKey, startTime, endTime, UserMusicSummaryService.CALCULATION_VERSION);
        int artists = topItemSummaryMapper.refreshArtistSummary(
                periodType, periodKey, startTime, endTime, UserMusicSummaryService.CALCULATION_VERSION);
        log.info("用户音乐周期Top汇总刷新完成: periodType={}, periodKey={}, songs={}, artists={}",
                periodType, periodKey, songs, artists);
        return songs + artists;
    }

    @Override
    public boolean hasCompleteCoverage(Long userId, String periodType, LocalDate startKey,
                                       LocalDate endKey, int expectedPeriods) {
        if (userId == null || periodType == null || startKey == null || endKey == null
                || endKey.isBefore(startKey) || expectedPeriods <= 0) {
            return false;
        }
        try {
            return topItemSummaryMapper.countCoveredPeriods(userId, periodType, startKey, endKey)
                    >= expectedPeriods;
        } catch (Exception e) {
            log.warn("读取用户音乐Top汇总覆盖范围失败: userId={}, periodType={}, error={}",
                    userId, periodType, e.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> aggregateTopItems(Long userId, String periodType,
                                                        LocalDate startKey, LocalDate endKey,
                                                        String itemType, int limit) {
        if (userId == null || periodType == null || startKey == null || endKey == null
                || itemType == null || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            return topItemSummaryMapper.selectAggregatedTopItems(
                    userId, periodType, startKey, endKey, itemType, Math.min(limit, 100));
        } catch (Exception e) {
            log.warn("读取用户音乐Top汇总失败: userId={}, periodType={}, itemType={}, error={}",
                    userId, periodType, itemType, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("items", topItemSummaryMapper.selectSummaryStatus());
        } catch (Exception e) {
            result.put("items", Collections.emptyList());
            result.put("error", e.getMessage());
        }
        result.put("calculationVersion", UserMusicSummaryService.CALCULATION_VERSION);
        result.put("maxBackfillDays", 366);
        result.put("maxBackfillMonths", 60);
        return result;
    }
}
