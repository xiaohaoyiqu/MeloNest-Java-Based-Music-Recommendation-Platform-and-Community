   
                      
   
package com.haoran.music.service.impl;

import com.haoran.music.entity.UserMusicDailySummary;
import com.haoran.music.entity.UserMusicMonthlySummary;
import com.haoran.music.mapper.UserMusicDailySummaryMapper;
import com.haoran.music.mapper.UserMusicMonthlySummaryMapper;
import com.haoran.music.service.UserMusicTopItemSummaryService;
import com.haoran.music.service.UserMusicSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class UserMusicSummaryServiceImpl implements UserMusicSummaryService {

    @Resource
    private UserMusicDailySummaryMapper dailySummaryMapper;

    @Resource
    private UserMusicMonthlySummaryMapper monthlySummaryMapper;

    @Resource
    private UserMusicTopItemSummaryService topItemSummaryService;

    private static final long MAX_DAILY_BACKFILL_DAYS = 366L;
    private static final long MAX_MONTHLY_BACKFILL_MONTHS = 60L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshDailySummary(LocalDate statDate) {
        LocalDate targetDate = statDate == null ? LocalDate.now().minusDays(1) : statDate;
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        int affected = dailySummaryMapper.refreshDailySummary(targetDate, start, end, CALCULATION_VERSION);
        topItemSummaryService.refreshDailyTopItems(targetDate);
        log.info("用户音乐日汇总刷新完成: statDate={}, affected={}", targetDate, affected);
        return affected;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int refreshMonthlySummary(YearMonth statMonth) {
        YearMonth targetMonth = statMonth == null ? YearMonth.now() : statMonth;
        LocalDate statMonthDate = targetMonth.atDay(1);
        LocalDateTime start = statMonthDate.atStartOfDay();
        LocalDateTime end = targetMonth.plusMonths(1).atDay(1).atStartOfDay();
        int affected = monthlySummaryMapper.refreshMonthlySummary(statMonthDate, start, end, CALCULATION_VERSION);
        topItemSummaryService.refreshMonthlyTopItems(targetMonth);
        log.info("用户音乐月汇总刷新完成: statMonth={}, affected={}", targetMonth, affected);
        return affected;
    }

    @Override
    public Map<String, Object> backfillDailySummaries(LocalDate startDate, LocalDate endDate) {
        LocalDate latestCompleteDate = LocalDate.now().minusDays(1);
        LocalDate start = startDate == null ? latestCompleteDate : startDate;
        LocalDate end = endDate == null ? start : endDate;
        if (end.isAfter(latestCompleteDate)) {
            throw new IllegalArgumentException("日汇总只回填完整日期，结束日期不能晚于昨天");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAILY_BACKFILL_DAYS) {
            throw new IllegalArgumentException("单次日汇总回填最多 " + MAX_DAILY_BACKFILL_DAYS + " 天");
        }

        int totalAffected = 0;
        List<String> failedDates = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            try {
                totalAffected += refreshDailySummary(cursor);
            } catch (Exception e) {
                failedDates.add(cursor + ":" + e.getMessage());
                log.warn("用户音乐日汇总回填失败: statDate={}, error={}", cursor, e.getClass().getSimpleName());
            }
            cursor = cursor.plusDays(1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("startDate", start);
        result.put("endDate", end);
        result.put("days", days);
        result.put("affectedRows", totalAffected);
        result.put("failedCount", failedDates.size());
        result.put("failedDates", failedDates);
        result.put("calculationVersion", CALCULATION_VERSION);
        return result;
    }

    @Override
    public Map<String, Object> backfillMonthlySummaries(YearMonth startMonth, YearMonth endMonth) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth start = startMonth == null ? currentMonth : startMonth;
        YearMonth end = endMonth == null ? start : endMonth;
        if (end.isAfter(currentMonth)) {
            throw new IllegalArgumentException("月汇总结束月份不能晚于当前月份");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("结束月份不能早于开始月份");
        }
        long months = ChronoUnit.MONTHS.between(start, end) + 1;
        if (months > MAX_MONTHLY_BACKFILL_MONTHS) {
            throw new IllegalArgumentException("单次月汇总回填最多 " + MAX_MONTHLY_BACKFILL_MONTHS + " 个月");
        }

        int totalAffected = 0;
        List<String> failedMonths = new ArrayList<>();
        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            try {
                totalAffected += refreshMonthlySummary(cursor);
            } catch (Exception e) {
                failedMonths.add(cursor + ":" + e.getMessage());
                log.warn("用户音乐月汇总回填失败: statMonth={}, error={}", cursor, e.getClass().getSimpleName());
            }
            cursor = cursor.plusMonths(1);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("startMonth", start.toString());
        result.put("endMonth", end.toString());
        result.put("months", months);
        result.put("affectedRows", totalAffected);
        result.put("failedCount", failedMonths.size());
        result.put("failedMonths", failedMonths);
        result.put("calculationVersion", CALCULATION_VERSION);
        return result;
    }

    @Override
    public Map<String, Object> getSummaryStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("daily", safeStatus(dailySummaryMapper.selectSummaryStatus()));
        result.put("monthly", safeStatus(monthlySummaryMapper.selectSummaryStatus()));
        result.put("calculationVersion", CALCULATION_VERSION);
        result.put("maxDailyBackfillDays", MAX_DAILY_BACKFILL_DAYS);
        result.put("maxMonthlyBackfillMonths", MAX_MONTHLY_BACKFILL_MONTHS);
        return result;
    }

    @Override
    public List<UserMusicDailySummary> listDailySummaries(Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return Collections.emptyList();
        }
        try {
            return dailySummaryMapper.selectByUserAndDateRange(userId, startDate, endDate);
        } catch (Exception e) {
            log.warn("读取用户音乐日汇总失败，回退明细计算: userId={}, startDate={}, endDate={}, error={}",
                    userId, startDate, endDate, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    @Override
    public List<UserMusicMonthlySummary> listMonthlySummaries(Long userId, YearMonth startMonth, YearMonth endMonth) {
        if (userId == null || startMonth == null || endMonth == null || endMonth.isBefore(startMonth)) {
            return Collections.emptyList();
        }
        try {
            return monthlySummaryMapper.selectByUserAndMonthRange(userId, startMonth.atDay(1), endMonth.atDay(1));
        } catch (Exception e) {
            log.warn("读取用户音乐月汇总失败，回退明细计算: userId={}, startMonth={}, endMonth={}, error={}",
                    userId, startMonth, endMonth, e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> safeStatus(Map<String, Object> status) {
        return status == null ? new HashMap<>() : status;
    }
}
