package com.haoran.music.service.impl;

import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.UserMusicMonthlySummary;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.TimeMachineRecommendService;
import com.haoran.music.service.UserMusicReportSnapshotService;
import com.haoran.music.service.UserMusicSummaryService;
import com.haoran.music.service.UserMusicTopItemSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;







@Slf4j
@Service
public class TimeMachineRecommendServiceImpl implements TimeMachineRecommendService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserMusicSummaryService userMusicSummaryService;

    @Autowired
    private UserMusicReportSnapshotService reportSnapshotService;

    @Autowired
    private UserMusicTopItemSummaryService topItemSummaryService;

    private static final String CACHE_PREFIX = "audio:timemachine:";
    private static final long SHORT_CACHE_MINUTES = 10L;
    private static final long YEARLY_CACHE_MINUTES = 120L;
    private static final int MIN_REPORT_YEAR = 2000;

    @Override
    public Map<String, Object> getThatDayHistory(Long userId, LocalDate date) {
        return cacheObjectMap(CACHE_PREFIX + "that-day-history:" + userId + ":" + date,
                () -> loadThatDayHistory(userId, date), SHORT_CACHE_MINUTES);
    }

    private Map<String, Object> loadThatDayHistory(Long userId, LocalDate date) {

        LocalDate lastYearDate = date.minusYears(1);

        String sql = "SELECT s.id, s.name, s.artist_names, s.cover, s.valence, s.energy, " +
                "lh.create_time as listen_time " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? " +
                "AND lh.create_time >= ? " +
                "AND lh.create_time < ? " +
                "ORDER BY lh.create_time DESC " +
                "LIMIT 50";

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, userId,
                startOfDay(lastYearDate), endExclusive(lastYearDate));


        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("lastYearDate", lastYearDate);
        result.put("records", records);
        result.put("count", records.size());
        return result;
    }

    @Override
    public List<Map<String, Object>> getThatDayRecommendation(Long userId, Integer limit) {
        return getThatDayRecommendation(userId, LocalDate.now(), limit);
    }

    @Override
    public List<Map<String, Object>> getThatDayRecommendation(Long userId, LocalDate targetDate, Integer limit) {
        int safeLimit = safeLimit(limit);
        if (targetDate == null || safeLimit <= 0) {
            return Collections.emptyList();
        }
        return cacheMapList(CACHE_PREFIX + "that-day-recommend:" + userId + ":" + targetDate + ":" + safeLimit,
                () -> loadThatDayRecommendation(userId, targetDate, safeLimit), SHORT_CACHE_MINUTES);
    }

    private List<Map<String, Object>> loadThatDayRecommendation(Long userId, LocalDate targetDate, int safeLimit) {
        Map<String, Object> historyData = getThatDayHistory(userId, targetDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) historyData.get("records");

        if (ObjectUtils.isEmpty(history)) {

            return getHistoricalHotSongsWithDetail(targetDate.minusYears(1), safeLimit);
        }


        double avgValence = 0, avgEnergy = 0;
        Set<String> genres = new HashSet<>();

        for (Map<String, Object> record : history) {
            Number valence = (Number) record.get("valence");
            Number energy = (Number) record.get("energy");
            if (valence != null) avgValence += valence.doubleValue();
            if (energy != null) avgEnergy += energy.doubleValue();
        }

        int count = history.size();
        if (count > 0) {
            avgValence /= count;
            avgEnergy /= count;
        }


        String recommendSql = "SELECT s.id, s.name, s.artist_names, s.cover, s.valence, s.energy, " +
                "s.play_count, s.favorite_count, s.main_type, s.duration, s.uploader_id " +
                "FROM song s " +
                "WHERE s.status = 1 AND s.deleted = 0 " +
                "AND s.valence BETWEEN ? AND ? " +
                "AND s.energy BETWEEN ? AND ? " +
                "AND s.id NOT IN (SELECT song_id FROM listen_history WHERE user_id = ?) " +
                "ORDER BY s.play_count DESC " +
                "LIMIT ?";

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                recommendSql,
                Math.max(0, avgValence - 0.15),
                Math.min(1, avgValence + 0.15),
                Math.max(0, avgEnergy - 0.15),
                Math.min(1, avgEnergy + 0.15),
                userId,
                candidateLimit(safeLimit)
        );
        return filterPublicUploaderRows(candidates).stream()
                .limit(safeLimit)
                .map(this::stripUploaderId)
                .collect(Collectors.toList());
    }








    @Override
    public Map<String, Object> comparePeriods(Long userId, Integer period1, Integer period2) {
        LocalDate now = LocalDate.now();
        LocalDate start1 = now.minusMonths(period1);
        LocalDate end1 = now.minusMonths(period1 > 0 ? period1 - 1 : 0);
        LocalDate start2 = now.minusMonths(period2);
        LocalDate end2 = now.minusMonths(period2 > 0 ? period2 - 1 : 0);


        Map<String, Object> stats1 = getPeriodStats(userId, start1, end1);
        Map<String, Object> stats2 = getPeriodStats(userId, start2, end2);


        List<String> preferredTypes1 = getPeriodPreferredTypes(userId, start1, end1);
        List<String> preferredTypes2 = getPeriodPreferredTypes(userId, start2, end2);

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> period1Data = new HashMap<>();
        period1Data.put("label", period1 + "个月前");
        period1Data.put("stats", stats1);
        period1Data.put("preferredTypes", preferredTypes1);
        result.put("period1", period1Data);

        Map<String, Object> period2Data = new HashMap<>();
        period2Data.put("label", period2 + "个月前");
        period2Data.put("stats", stats2);
        period2Data.put("preferredTypes", preferredTypes2);
        result.put("period2", period2Data);


        Integer listenCount1 = ((Number) stats1.getOrDefault("listenCount", 0)).intValue();
        Integer listenCount2 = ((Number) stats2.getOrDefault("listenCount", 0)).intValue();
        result.put("listenCountChange", listenCount2 - listenCount1);


        Set<String> newTypes = new HashSet<>(preferredTypes2);
        newTypes.removeAll(preferredTypes1);
        Set<String> lostTypes = new HashSet<>(preferredTypes1);
        lostTypes.removeAll(preferredTypes2);
        result.put("newTypes", newTypes);
        result.put("lostTypes", lostTypes);

        return result;
    }




    private Map<String, Object> getPeriodStats(Long userId, LocalDate start, LocalDate end) {
        String sql = "SELECT COUNT(*) as listen_count, " +
                "COUNT(DISTINCT song_id) as unique_songs, " +
                "AVG(s.valence) as avg_valence, " +
                "AVG(s.energy) as avg_energy " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ?";

        return jdbcTemplate.queryForMap(sql, userId, startOfDay(start), endExclusive(end));
    }




    private List<String> getPeriodPreferredTypes(Long userId, LocalDate start, LocalDate end) {
        String sql = "SELECT s.main_type, COUNT(*) as count " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                "AND s.main_type IS NOT NULL " +
                "GROUP BY s.main_type " +
                "ORDER BY count DESC " +
                "LIMIT 3";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId,
                startOfDay(start), endExclusive(end));
        return results.stream()
                .map(row -> (String) row.get("main_type"))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getMusicTimeline(Long userId, Integer months) {
        int safeMonths = months == null || months <= 0 ? 12 : Math.min(months, 60);
        return cacheMapList(CACHE_PREFIX + "timeline:" + userId + ":" + safeMonths + ":" + YearMonth.now(),
                () -> loadMusicTimeline(userId, safeMonths), SHORT_CACHE_MINUTES);
    }

    private List<Map<String, Object>> loadMusicTimeline(Long userId, int safeMonths) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        YearMonth earliestMonth = currentMonth.minusMonths(safeMonths - 1L);
        LocalDateTime rangeStart = startOfMonth(earliestMonth);
        LocalDateTime rangeEnd = nextMonthStart(currentMonth);

        List<UserMusicMonthlySummary> summaries = userMusicSummaryService.listMonthlySummaries(
                userId, earliestMonth, currentMonth);
        if (hasMonthlyCoverage(summaries, earliestMonth, currentMonth)) {
            return buildTimelineFromMonthlySummaries(summaries, currentMonth, safeMonths);
        }

        String statsSql = "SELECT " +
                "YEAR(lh.create_time) as year, " +
                "MONTH(lh.create_time) as month, " +
                "COUNT(*) as listen_count, " +
                "COUNT(DISTINCT song_id) as unique_songs, " +
                "SUM(s.duration) / 60 as total_minutes, " +
                "AVG(s.valence) as avg_mood, " +
                "AVG(s.energy) as avg_energy " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? " +
                "AND lh.create_time >= ? " +
                "AND lh.create_time < ? " +
                "GROUP BY YEAR(lh.create_time), MONTH(lh.create_time)";

        Map<String, Map<String, Object>> statsByMonth = jdbcTemplate.queryForList(
                        statsSql, userId, rangeStart, rangeEnd).stream()
                .collect(Collectors.toMap(
                        row -> monthKey(((Number) row.get("year")).intValue(), ((Number) row.get("month")).intValue()),
                        row -> row,
                        (left, right) -> left
                ));

        String topSongSql = "SELECT " +
                "YEAR(lh.create_time) as year, " +
                "MONTH(lh.create_time) as month, " +
                "s.id, s.name, s.artist_names, COUNT(*) as count " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? " +
                "AND lh.create_time >= ? " +
                "AND lh.create_time < ? " +
                "GROUP BY YEAR(lh.create_time), MONTH(lh.create_time), s.id, s.name, s.artist_names " +
                "ORDER BY year DESC, month DESC, count DESC";

        Map<String, Map<String, Object>> topSongByMonth = new HashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(topSongSql, userId, rangeStart, rangeEnd)) {
            String key = monthKey(((Number) row.get("year")).intValue(), ((Number) row.get("month")).intValue());
            topSongByMonth.putIfAbsent(key, row);
        }

        for (int i = 0; i < safeMonths; i++) {
            YearMonth yearMonth = currentMonth.minusMonths(i);
            String key = monthKey(yearMonth.getYear(), yearMonth.getMonthValue());
            Map<String, Object> monthData = statsByMonth.getOrDefault(key, Collections.emptyMap());

            Map<String, Object> timelineItem = new HashMap<>();
            timelineItem.put("year", yearMonth.getYear());
            timelineItem.put("month", yearMonth.getMonthValue());
            timelineItem.put("label", yearMonth.getYear() + "年" + yearMonth.getMonthValue() + "月");
            timelineItem.put("listenCount", monthData.getOrDefault("listen_count", 0));
            timelineItem.put("uniqueSongs", monthData.getOrDefault("unique_songs", 0));
            timelineItem.put("totalMinutes", monthData.getOrDefault("total_minutes", 0));
            timelineItem.put("avgMood", monthData.getOrDefault("avg_mood", 0.5));
            timelineItem.put("avgEnergy", monthData.getOrDefault("avg_energy", 0.5));

            Map<String, Object> topSong = topSongByMonth.get(key);
            if (topSong != null) {
                timelineItem.put("topSong", topSong);
            }
            timeline.add(timelineItem);
        }

        return timeline;
    }

    @Override
    public Map<String, Object> getYearlyMemory(Long userId, Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear() - 1;        
        }
        if (year < MIN_REPORT_YEAR || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("报告年份必须在2000到当前年份之间");
        }
        Integer resolvedYear = year;
        return reportSnapshotService.getOrCreate(userId, "yearly_memory", String.valueOf(resolvedYear),
                dataUntilForYear(resolvedYear), UserMusicSummaryService.CALCULATION_VERSION, YEARLY_CACHE_MINUTES,
                () -> loadYearlyMemory(userId, resolvedYear));
    }

    private Map<String, Object> loadYearlyMemory(Long userId, Integer year) {
        LocalDateTime yearStart = yearStart(year);
        LocalDateTime nextYearStart = nextYearStart(year);
        List<UserMusicMonthlySummary> summaries = userMusicSummaryService.listMonthlySummaries(
                userId, YearMonth.of(year, 1), YearMonth.of(year, 12));

        Map<String, Object> stats;
        List<Map<String, Object>> monthlyTrend;
        List<Map<String, Object>> moodCurve;
        boolean summaryBacked = hasMonthlyCoverage(summaries, YearMonth.of(year, 1), YearMonth.of(year, 12));
        if (summaryBacked) {
            stats = buildYearlyStatsFromMonthlySummaries(summaries);
            monthlyTrend = buildMonthlyTrendFromSummaries(summaries);
            moodCurve = buildMoodCurveFromSummaries(summaries);
        } else {

            String statsSql = "SELECT " +
                    "COUNT(*) as total_listens, " +
                    "COUNT(DISTINCT song_id) as unique_songs, " +
                    "COUNT(DISTINCT DATE(create_time)) as active_days, " +
                    "SUM(s.duration) / 60 as total_minutes " +
                    "FROM listen_history lh " +
                    "LEFT JOIN song s ON lh.song_id = s.id " +
                    "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ?";

            stats = jdbcTemplate.queryForMap(statsSql, userId, yearStart, nextYearStart);


            String monthlyTrendSql = "SELECT " +
                    "MONTH(create_time) as month, " +
                    "COUNT(*) as count, " +
                    "COUNT(DISTINCT song_id) as unique_songs " +
                    "FROM listen_history " +
                    "WHERE user_id = ? AND create_time >= ? AND create_time < ? " +
                    "GROUP BY MONTH(create_time) " +
                    "ORDER BY month";

            monthlyTrend = jdbcTemplate.queryForList(monthlyTrendSql, userId, yearStart, nextYearStart);


            String moodCurveSql = "SELECT " +
                    "MONTH(create_time) as month, " +
                    "AVG(s.valence) as avg_valence, " +
                    "AVG(s.energy) as avg_energy " +
                    "FROM listen_history lh " +
                    "LEFT JOIN song s ON lh.song_id = s.id " +
                    "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                    "GROUP BY MONTH(create_time) " +
                    "ORDER BY month";

            moodCurve = jdbcTemplate.queryForList(moodCurveSql, userId, yearStart, nextYearStart);
        }

        LocalDate topStartKey = LocalDate.of(year, 1, 1);
        LocalDate topEndKey = LocalDate.of(year, 12, 1);
        boolean topSummaryBacked = topItemSummaryService.hasCompleteCoverage(
                userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY,
                topStartKey, topEndKey, 12);
        List<Map<String, Object>> topSongs;
        List<Map<String, Object>> topArtists;
        if (topSummaryBacked) {
            topSongs = mapTopSongs(topItemSummaryService.aggregateTopItems(
                    userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY,
                    topStartKey, topEndKey, UserMusicTopItemSummaryService.ITEM_SONG, 10));
            topArtists = mapTopArtists(topItemSummaryService.aggregateTopItems(
                    userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY,
                    topStartKey, topEndKey, UserMusicTopItemSummaryService.ITEM_ARTIST, 5));
        } else {
            String topSongsSql = "SELECT s.id, s.name, s.artist_names, s.cover, COUNT(*) as play_count " +
                    "FROM listen_history lh " +
                    "LEFT JOIN song s ON lh.song_id = s.id " +
                    "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                    "GROUP BY s.id " +
                    "ORDER BY play_count DESC " +
                    "LIMIT 10";
            topSongs = jdbcTemplate.queryForList(topSongsSql, userId, yearStart, nextYearStart);

            String topArtistsSql = "SELECT s.artist_names, COUNT(*) as play_count " +
                    "FROM listen_history lh " +
                    "LEFT JOIN song s ON lh.song_id = s.id " +
                    "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                    "AND s.artist_names IS NOT NULL " +
                    "GROUP BY s.artist_names " +
                    "ORDER BY play_count DESC " +
                    "LIMIT 5";
            topArtists = jdbcTemplate.queryForList(topArtistsSql, userId, yearStart, nextYearStart);
        }

        Map<String, Object> memory = new HashMap<>();
        memory.put("year", year);
        memory.put("stats", stats);
        memory.put("topSongs", topSongs);
        memory.put("topArtists", topArtists);
        memory.put("monthlyTrend", monthlyTrend);
        memory.put("moodCurve", moodCurve);
        memory.put("dataSource", summaryBacked && topSummaryBacked
                ? "monthly_summary_with_top_item_summary" : "listen_history");
        memory.put("calculationVersion", UserMusicSummaryService.CALCULATION_VERSION);


        List<String> tags = generateYearlyTags(stats, topSongs);
        memory.put("tags", tags);

        return memory;
    }

    private List<Map<String, Object>> mapTopSongs(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> song = new HashMap<>();
            song.put("id", row.get("itemId"));
            song.put("name", row.get("itemName"));
            song.put("artist_names", row.get("artistNames"));
            song.put("cover", row.get("itemCover"));
            song.put("play_count", row.get("playCount"));
            return song;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> mapTopArtists(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> artist = new HashMap<>();
            artist.put("artist_id", row.get("itemId"));
            artist.put("artistName", row.get("itemName"));
            artist.put("artist_names", row.get("itemName"));
            artist.put("play_count", row.get("playCount"));
            return artist;
        }).collect(Collectors.toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> cacheMapList(String key, Supplier<List<Map<String, Object>>> supplier,
                                                   long ttlMinutes) {
        try {
            return (List<Map<String, Object>>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("时光机缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> cacheObjectMap(String key, Supplier<Map<String, Object>> supplier, long ttlMinutes) {
        try {
            return (Map<String, Object>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, Map.class);
        } catch (Exception e) {
            log.warn("时光机缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }




    private List<Map<String, Object>> getHistoricalHotSongsWithDetail(LocalDate date, Integer limit) {
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT DISTINCT s.id, s.name, s.artist_names, s.cover, s.valence, s.energy, " +
                "s.play_count, s.favorite_count, s.main_type, s.duration, s.uploader_id " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.create_time >= ? " +
                "AND lh.create_time < ? " +
                "AND s.status = 1 AND s.deleted = 0 " +
                "ORDER BY s.play_count DESC " +
                "LIMIT ?";

        YearMonth targetMonth = YearMonth.from(date);
        return filterPublicUploaderRows(jdbcTemplate.queryForList(sql, startOfMonth(targetMonth),
                nextMonthStart(targetMonth), candidateLimit(safeLimit))).stream()
                .limit(safeLimit)
                .map(this::stripUploaderId)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> filterPublicUploaderRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = rows.stream()
                .map(row -> toLong(row.get("uploader_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds, ids -> userMapper.selectBatchIds(ids));
        return rows.stream()
                .filter(row -> {
                    Long uploaderId = toLong(row.get("uploader_id"));
                    return uploaderId == null || allowedUploaderIds.contains(uploaderId);
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> stripUploaderId(Map<String, Object> row) {
        Map<String, Object> copy = new HashMap<>(row);
        copy.remove("uploader_id");
        return copy;
    }

    private List<Map<String, Object>> buildTimelineFromMonthlySummaries(List<UserMusicMonthlySummary> summaries,
                                                                        YearMonth currentMonth,
                                                                        int safeMonths) {
        Map<String, UserMusicMonthlySummary> summaryByMonth = summaries.stream()
                .filter(summary -> summary.getStatMonth() != null)
                .collect(Collectors.toMap(
                        summary -> monthKey(summary.getStatMonth().getYear(), summary.getStatMonth().getMonthValue()),
                        summary -> summary,
                        (left, right) -> left
                ));

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (int i = 0; i < safeMonths; i++) {
            YearMonth yearMonth = currentMonth.minusMonths(i);
            UserMusicMonthlySummary summary = summaryByMonth.get(monthKey(yearMonth.getYear(), yearMonth.getMonthValue()));

            Map<String, Object> timelineItem = new HashMap<>();
            timelineItem.put("year", yearMonth.getYear());
            timelineItem.put("month", yearMonth.getMonthValue());
            timelineItem.put("label", yearMonth.getYear() + "年" + yearMonth.getMonthValue() + "月");
            timelineItem.put("listenCount", summary == null ? 0 : safeInt(summary.getPlayCount()));
            timelineItem.put("uniqueSongs", summary == null ? 0 : safeInt(summary.getUniqueSongCount()));
            timelineItem.put("totalMinutes", summary == null ? 0 : safeLong(summary.getPlaySeconds()) / 60);
            timelineItem.put("avgMood", summary == null ? 0.5 : defaultDouble(summary.getAvgValence(), 0.5));
            timelineItem.put("avgEnergy", summary == null ? 0.5 : defaultDouble(summary.getAvgEnergy(), 0.5));
            timelineItem.put("dataSource", summary == null ? "empty" : "monthly_summary");

            if (summary != null && summary.getTopSongId() != null) {
                Map<String, Object> topSong = new HashMap<>();
                topSong.put("id", summary.getTopSongId());
                topSong.put("name", summary.getTopSongName());
                topSong.put("artist_names", summary.getTopSongArtistNames());
                timelineItem.put("topSong", topSong);
            }

            timeline.add(timelineItem);
        }
        return timeline;
    }

    private boolean hasMonthlyCoverage(List<UserMusicMonthlySummary> summaries, YearMonth startMonth, YearMonth endMonth) {
        if (summaries == null || summaries.isEmpty() || endMonth.isBefore(startMonth)) {
            return false;
        }
        Set<YearMonth> coveredMonths = summaries.stream()
                .map(UserMusicMonthlySummary::getStatMonth)
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .collect(Collectors.toSet());
        YearMonth cursor = startMonth;
        while (!cursor.isAfter(endMonth)) {
            if (!coveredMonths.contains(cursor)) {
                return false;
            }
            cursor = cursor.plusMonths(1);
        }
        return true;
    }

    private Map<String, Object> buildYearlyStatsFromMonthlySummaries(List<UserMusicMonthlySummary> summaries) {
        Map<String, Object> stats = new HashMap<>();
        int totalListens = 0;
        int uniqueSongs = 0;
        int activeDays = 0;
        long totalSeconds = 0L;
        long featurePlayCount = 0L;
        double weightedFeatureCoverage = 0.0;

        for (UserMusicMonthlySummary summary : summaries) {
            int playCount = safeInt(summary.getPlayCount());
            totalListens += playCount;
            uniqueSongs += safeInt(summary.getUniqueSongCount());
            activeDays += safeInt(summary.getActiveDays());
            totalSeconds += safeLong(summary.getPlaySeconds());
            featurePlayCount += playCount;
            weightedFeatureCoverage += playCount * safeDouble(summary.getFeatureCoverage());
        }

        stats.put("total_listens", totalListens);
        stats.put("unique_songs", uniqueSongs);
        stats.put("active_days", activeDays);
        stats.put("total_minutes", totalSeconds / 60);
        stats.put("feature_coverage", featurePlayCount == 0 ? 0.0 : weightedFeatureCoverage / featurePlayCount);
        stats.put("approximate_unique_songs", true);
        return stats;
    }

    private List<Map<String, Object>> buildMonthlyTrendFromSummaries(List<UserMusicMonthlySummary> summaries) {
        return summaries.stream()
                .filter(summary -> summary.getStatMonth() != null)
                .map(summary -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("month", summary.getStatMonth().getMonthValue());
                    row.put("count", safeInt(summary.getPlayCount()));
                    row.put("unique_songs", safeInt(summary.getUniqueSongCount()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildMoodCurveFromSummaries(List<UserMusicMonthlySummary> summaries) {
        return summaries.stream()
                .filter(summary -> summary.getStatMonth() != null)
                .map(summary -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("month", summary.getStatMonth().getMonthValue());
                    row.put("avg_valence", defaultDouble(summary.getAvgValence(), 0.5));
                    row.put("avg_energy", defaultDouble(summary.getAvgEnergy(), 0.5));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private int safeLimit(Integer limit) {
        return limit == null || limit <= 0 ? 0 : Math.min(limit, 100);
    }

    private int candidateLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        long expanded = Math.max((long) limit * 3L, (long) limit + 10L);
        return (int) Math.min(expanded, 1000L);
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    private LocalDateTime startOfMonth(YearMonth yearMonth) {
        return yearMonth.atDay(1).atStartOfDay();
    }

    private LocalDateTime nextMonthStart(YearMonth yearMonth) {
        return yearMonth.plusMonths(1).atDay(1).atStartOfDay();
    }

    private LocalDateTime yearStart(Integer year) {
        return LocalDate.of(year, 1, 1).atStartOfDay();
    }

    private LocalDateTime nextYearStart(Integer year) {
        return LocalDate.of(year + 1, 1, 1).atStartOfDay();
    }

    private LocalDateTime dataUntilForYear(Integer year) {
        LocalDateTime endOfYear = nextYearStart(year);
        LocalDateTime now = LocalDateTime.now();
        return endOfYear.isBefore(now) ? endOfYear.minusSeconds(1) : now;
    }

    private String monthKey(int year, int month) {
        return year + "-" + month;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double safeDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double defaultDouble(BigDecimal value, double defaultValue) {
        return value == null ? defaultValue : value.doubleValue();
    }




    private List<String> generateYearlyTags(Map<String, Object> stats, List<Map<String, Object>> topSongs) {
        List<String> tags = new ArrayList<>();

        int totalListens = ((Number) stats.getOrDefault("total_listens", 0)).intValue();
        int uniqueSongs = ((Number) stats.getOrDefault("unique_songs", 0)).intValue();

        if (totalListens > 5000) {
            tags.add("音乐狂热者");
        } else if (totalListens > 2000) {
            tags.add("重度音乐爱好者");
        } else if (totalListens > 500) {
            tags.add("音乐爱好者");
        }

        if (uniqueSongs > 500) {
            tags.add("探索达人");
        } else if (uniqueSongs > 200) {
            tags.add("音乐探索者");
        }

        if (tags.isEmpty()) {
            tags.add("音乐聆听者");
        }

        return tags;
    }
}
