package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.entity.ListenHistory;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.UserMusicDailySummary;
import com.haoran.music.entity.UserMusicMonthlySummary;
import com.haoran.music.mapper.ListenHistoryMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.SongLikeMapper;
import com.haoran.music.service.MusicHealthReportService;
import com.haoran.music.service.UserMusicReportSnapshotService;
import com.haoran.music.service.UserMusicSummaryService;
import com.haoran.music.service.UserMusicTopItemSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

   
             
  
                      
   
@Slf4j
@Service
public class MusicHealthReportServiceImpl implements MusicHealthReportService {

    @Resource
    private ListenHistoryMapper listenHistoryMapper;

    @Resource
    private SongMapper songMapper;

    @Resource
    private SongLikeMapper songLikeMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private UserMusicSummaryService userMusicSummaryService;

    @Resource
    private UserMusicReportSnapshotService reportSnapshotService;

    @Resource
    private UserMusicTopItemSummaryService topItemSummaryService;

    private static final String CACHE_PREFIX = "audio:health:";
    private static final long SHORT_CACHE_MINUTES = 10L;
    private static final long REPORT_CACHE_MINUTES = 15L;
    private static final long YEARLY_CACHE_MINUTES = 120L;
    private static final int MIN_REPORT_YEAR = 2000;

    @Override
    public Map<String, Object> generateReport(Long userId) {
        LocalDate today = LocalDate.now();
        return reportSnapshotService.getOrCreate(userId, "health_report", today.toString(),
                LocalDateTime.now(), UserMusicSummaryService.CALCULATION_VERSION, REPORT_CACHE_MINUTES,
                () -> loadReport(userId));
    }

    private Map<String, Object> loadReport(Long userId) {
        log.debug("生成音乐体检报告: userId={}", userId);

        Map<String, Object> report = new HashMap<>();

               
        report.put("listeningSummary", getListeningSummary(userId));

               
        report.put("explorationScore", getExplorationScore(userId));

                 
        report.put("timeDistribution", getListeningTimeDistribution(userId));

               
        report.put("musicFingerprint", getMusicFingerprint(userId));

                     
        int lastYear = LocalDate.now().getYear() - 1;
        report.put("yearlyReport", getYearlyReport(userId, lastYear));

        return report;
    }

    @Override
    public Map<String, Object> getListeningSummary(Long userId) {
        return cacheObjectMap(todayCacheKey("summary", userId), () -> loadListeningSummary(userId), SHORT_CACHE_MINUTES);
    }

    private Map<String, Object> loadListeningSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(29);
        List<UserMusicDailySummary> summaries = userMusicSummaryService.listDailySummaries(
                userId, startDate, today.minusDays(1));
        if (hasDailyCoverage(summaries, startDate, today.minusDays(1))) {
            return buildListeningSummaryFromDailySummaries(userId, summaries, today);
        }

        List<ListenHistory> histories = loadHistories(userId, LocalDateTime.now().minusDays(30), LocalDateTime.now());
        return buildListeningSummary(calculateRawMetrics(histories), 30, "listen_history", false);
    }

    @Override
    public Integer getExplorationScore(Long userId) {
        return cacheInteger(todayCacheKey("exploration-score", userId),
                () -> loadExplorationScore(userId), SHORT_CACHE_MINUTES);
    }

    private Integer loadExplorationScore(Long userId) {
        log.debug("计算探索指数: userId={}", userId);

                                                     

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);

                     
        LambdaQueryWrapper<ListenHistory> recentWrapper = new LambdaQueryWrapper<>();
        recentWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, thirtyDaysAgo);

        List<ListenHistory> recentHistories = listenHistoryMapper.selectList(recentWrapper);
        Set<Long> recentSongIds = recentHistories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

                       
        LambdaQueryWrapper<ListenHistory> pastWrapper = new LambdaQueryWrapper<>();
        pastWrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, ninetyDaysAgo)
                .lt(ListenHistory::getCreateTime, thirtyDaysAgo);

        List<ListenHistory> pastHistories = listenHistoryMapper.selectList(pastWrapper);
        Set<Long> pastSongIds = pastHistories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

                                         
        Set<Long> newSongIds = new HashSet<>(recentSongIds);
        newSongIds.removeAll(pastSongIds);
        int newSongRatio = recentSongIds.isEmpty() ? 0 :
                (int) (newSongIds.size() * 40.0 / Math.max(recentSongIds.size(), 1));

        Map<Long, Song> recentSongMap = loadSongMap(recentSongIds);

                
        Set<String> genres = recentSongMap.values().stream()
                .map(Song::getMainType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        int genreDiversity = Math.min(genres.size() * 5, 30);         

                
        Set<String> artists = recentSongMap.values().stream()
                .map(Song::getArtistNames)
                .filter(Objects::nonNull)
                .flatMap(a -> Arrays.stream(a.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        int artistDiversity = Math.min(artists.size() * 2, 30);         

        int score = newSongRatio + genreDiversity + artistDiversity;
        return Math.max(0, Math.min(100, score));
    }

    @Override
    public Map<String, Integer> getListeningTimeDistribution(Long userId) {
        return cacheIntegerMap(todayCacheKey("time-distribution", userId),
                () -> loadListeningTimeDistribution(userId), SHORT_CACHE_MINUTES);
    }

    private Map<String, Integer> loadListeningTimeDistribution(Long userId) {
        Map<String, Integer> distribution = new HashMap<>();

                 
        distribution.put("深夜(0-6点)", 0);
        distribution.put("早晨(6-12点)", 0);
        distribution.put("下午(12-18点)", 0);
        distribution.put("晚上(18-24点)", 0);

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, thirtyDaysAgo);

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

        for (ListenHistory h : histories) {
            if (h.getCreateTime() == null) continue;

            int hour = h.getCreateTime().getHour();

            if (hour >= 0 && hour < 6) {
                distribution.merge("深夜(0-6点)", 1, Integer::sum);
            } else if (hour >= 6 && hour < 12) {
                distribution.merge("早晨(6-12点)", 1, Integer::sum);
            } else if (hour >= 12 && hour < 18) {
                distribution.merge("下午(12-18点)", 1, Integer::sum);
            } else {
                distribution.merge("晚上(18-24点)", 1, Integer::sum);
            }
        }

        return distribution;
    }

    @Override
    public Map<String, Object> getYearlyReport(Long userId, Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear() - 1;
        }
        if (year < MIN_REPORT_YEAR || year > LocalDate.now().getYear()) {
            throw new IllegalArgumentException("报告年份必须在2000到当前年份之间");
        }
        Integer resolvedYear = year;
        return reportSnapshotService.getOrCreate(userId, "yearly_report", String.valueOf(resolvedYear),
                dataUntilForYear(resolvedYear), UserMusicSummaryService.CALCULATION_VERSION, YEARLY_CACHE_MINUTES,
                () -> loadYearlyReport(userId, resolvedYear));
    }

    private Map<String, Object> loadYearlyReport(Long userId, Integer year) {
        log.debug("获取年度报告: userId={}, year={}", userId, year);

        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year + 1, 1, 1, 0, 0);

        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, startOfYear)
                .lt(ListenHistory::getCreateTime, endOfYear);

        List<UserMusicMonthlySummary> monthlySummaries = userMusicSummaryService.listMonthlySummaries(
                userId, YearMonth.of(year, 1), YearMonth.of(year, 12));
        boolean monthlySummaryCovered = hasMonthlyCoverage(monthlySummaries, YearMonth.of(year, 1), YearMonth.of(year, 12));
        LocalDate startKey = LocalDate.of(year, 1, 1);
        LocalDate endKey = LocalDate.of(year, 12, 1);
        boolean topSummaryCovered = topItemSummaryService.hasCompleteCoverage(
                userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY, startKey, endKey, 12);
        List<ListenHistory> histories = !monthlySummaryCovered || !topSummaryCovered
                ? listenHistoryMapper.selectList(wrapper)
                : Collections.emptyList();

        Map<String, Object> report = new HashMap<>();
        report.put("year", year);
        if (monthlySummaryCovered) {
            report.put("totalPlays", monthlySummaries.stream()
                    .mapToInt(summary -> safeInt(summary.getPlayCount()))
                    .sum());
            report.put("featureCoverage", calculateFeatureCoverage(monthlySummaries));
            report.put("dataSource", topSummaryCovered
                    ? "monthly_summary_with_top_item_summary"
                    : "monthly_summary_with_history_artist_stats");
        } else {
            report.put("totalPlays", histories.size());
            report.put("dataSource", "listen_history");
        }

        List<Map<String, Object>> topArtists;
        List<Map<String, Object>> topSongs;
        if (topSummaryCovered) {
            topArtists = mapTopArtists(topItemSummaryService.aggregateTopItems(
                    userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY,
                    startKey, endKey, UserMusicTopItemSummaryService.ITEM_ARTIST, 10));
            topSongs = mapTopSongs(topItemSummaryService.aggregateTopItems(
                    userId, UserMusicTopItemSummaryService.PERIOD_MONTHLY,
                    startKey, endKey, UserMusicTopItemSummaryService.ITEM_SONG, 10));
        } else {
            Map<Long, ArtistStat> artistStats = new HashMap<>();
            Set<Long> songIds = histories.stream()
                    .map(ListenHistory::getSongId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, Song> songMap = loadSongMap(songIds);
            Map<Long, Integer> songCounts = new HashMap<>();
            for (ListenHistory history : histories) {
                if (history.getSongId() != null) {
                    songCounts.merge(history.getSongId(), 1, Integer::sum);
                }
                Song song = songMap.get(history.getSongId());
                if (song == null) {
                    continue;
                }
                List<Long> artistIds = resolveArtistIds(song);
                String artistName = normalizeArtistNames(song.getArtistNames());
                for (Long artistId : artistIds) {
                    ArtistStat stat = artistStats.computeIfAbsent(artistId, id -> new ArtistStat(id, artistName));
                    if (stat.artistName == null && artistName != null) {
                        stat.artistName = artistName;
                    }
                    stat.playCount++;
                }
            }
            topArtists = artistStats.values().stream()
                    .sorted(Comparator.comparingInt((ArtistStat stat) -> stat.playCount).reversed())
                    .limit(10)
                    .map(stat -> {
                        Map<String, Object> artistInfo = new HashMap<>();
                        artistInfo.put("artistId", stat.artistId);
                        artistInfo.put("artistName", stat.artistName);
                        artistInfo.put("playCount", stat.playCount);
                        return artistInfo;
                    })
                    .collect(Collectors.toList());
            topSongs = songCounts.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(10)
                    .map(entry -> {
                        Song song = songMap.get(entry.getKey());
                        Map<String, Object> songInfo = new HashMap<>();
                        songInfo.put("id", entry.getKey());
                        songInfo.put("name", song == null ? null : song.getName());
                        songInfo.put("artist_names", song == null ? null : song.getArtistNames());
                        songInfo.put("cover", song == null ? null : song.getCover());
                        songInfo.put("play_count", entry.getValue());
                        return songInfo;
                    })
                    .collect(Collectors.toList());
        }

        report.put("topArtists", topArtists);
        report.put("topSongs", topSongs);

               
        Map<String, Integer> monthlyDistribution = monthlySummaryCovered
                ? buildMonthlyDistributionFromSummaries(monthlySummaries)
                : buildMonthlyDistributionFromHistories(histories);
        report.put("monthlyDistribution", monthlyDistribution);
        report.put("calculationVersion", UserMusicSummaryService.CALCULATION_VERSION);

        return report;
    }

    private List<Map<String, Object>> mapTopArtists(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> artist = new HashMap<>();
            artist.put("artistId", row.get("itemId"));
            artist.put("artistName", row.get("itemName"));
            artist.put("playCount", row.get("playCount"));
            return artist;
        }).collect(Collectors.toList());
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

    @Override
    public Map<String, Object> getMusicFingerprint(Long userId) {
        return cacheObjectMap(todayCacheKey("fingerprint", userId),
                () -> loadMusicFingerprint(userId), SHORT_CACHE_MINUTES);
    }

    private Map<String, Object> loadMusicFingerprint(Long userId) {
        log.debug("获取音乐指纹: userId={}", userId);

        Map<String, Object> fingerprint = new HashMap<>();
        fingerprint.put("userId", userId);

                     
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, thirtyDaysAgo);

        List<ListenHistory> histories = listenHistoryMapper.selectList(wrapper);

        Set<Long> songIds = histories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (songIds.isEmpty()) {
            fingerprint.put("tags", Arrays.asList("新用户"));
            return fingerprint;
        }

        List<Song> songs = songMapper.selectBatchIds(new ArrayList<>(songIds));

               
        Map<String, Integer> genreCounts = songs.stream()
                .map(Song::getMainType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(g -> g, Collectors.summingInt(e -> 1)));

        List<String> topGenres = genreCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        fingerprint.put("genres", topGenres);

                 
        Double avgEnergy = songs.stream()
                .map(Song::getEnergy)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        Double avgValence = songs.stream()
                .map(Song::getValence)
                .filter(Objects::nonNull)
                .map(BigDecimal::doubleValue)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);

        fingerprint.put("energyLevel", avgEnergy > 0.6 ? "高能量" : avgEnergy < 0.4 ? "低能量" : "中等能量");
        fingerprint.put("mood", avgValence > 0.6 ? "积极" : avgValence < 0.4 ? "消极" : "中性");

             
        List<String> tags = new ArrayList<>();
        tags.addAll(topGenres);
        tags.add(fingerprint.get("energyLevel").toString());
        tags.add(fingerprint.get("mood").toString());

                 
        int morningCount = 0, nightCount = 0;
        for (ListenHistory h : histories) {
            if (h.getCreateTime() == null) continue;
            int hour = h.getCreateTime().getHour();
            if (hour >= 6 && hour < 12) morningCount++;
            if (hour >= 22 || hour < 6) nightCount++;
        }

        if (morningCount > nightCount * 1.5) {
            tags.add("早起音乐爱好者");
        } else if (nightCount > morningCount * 1.5) {
            tags.add("夜猫子");
        }

        fingerprint.put("tags", tags);

        return fingerprint;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> cacheObjectMap(String key, Supplier<Map<String, Object>> supplier, long ttlMinutes) {
        try {
            return (Map<String, Object>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, Map.class);
        } catch (Exception e) {
            log.warn("音乐报告缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Integer> cacheIntegerMap(String key, Supplier<Map<String, Integer>> supplier, long ttlMinutes) {
        try {
            return (Map<String, Integer>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, Map.class);
        } catch (Exception e) {
            log.warn("音乐报告缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    private Integer cacheInteger(String key, Supplier<Integer> supplier, long ttlMinutes) {
        try {
            return CacheHelper.getOrLoad(redisUtils, key, supplier, ttlMinutes, TimeUnit.MINUTES, Integer.class);
        } catch (Exception e) {
            log.warn("音乐报告缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    private Map<String, Object> buildListeningSummaryFromDailySummaries(Long userId,
                                                                        List<UserMusicDailySummary> summaries,
                                                                        LocalDate today) {
        ListenMetrics todayMetrics = calculateRawMetrics(loadHistories(userId, today.atStartOfDay(), LocalDateTime.now()));

        ListenMetrics metrics = new ListenMetrics();
        long featureSamples = todayMetrics.featureSamples;
        metrics.playCount = todayMetrics.playCount;
        metrics.uniqueSongCount = todayMetrics.uniqueSongCount;
        metrics.playSeconds = todayMetrics.playSeconds;

        for (UserMusicDailySummary summary : summaries) {
            int playCount = safeInt(summary.getPlayCount());
            metrics.playCount += playCount;
            metrics.uniqueSongCount += safeInt(summary.getUniqueSongCount());
            metrics.playSeconds += safeLong(summary.getPlaySeconds());
            featureSamples += Math.round(playCount * safeDouble(summary.getFeatureCoverage()));
        }
        metrics.featureSamples = featureSamples;

        Map<String, Object> result = buildListeningSummary(metrics, 30, "daily_summary_with_today_delta", true);
        result.put("summaryDays", summaries.size());
        return result;
    }

    private Map<String, Object> buildListeningSummary(ListenMetrics metrics, int days,
                                                      String dataSource, boolean approximateUniqueSongs) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalPlays", metrics.playCount);
        summary.put("uniqueSongs", metrics.uniqueSongCount);
        summary.put("totalMinutes", metrics.playSeconds / 60);
        summary.put("avgDailyPlays", metrics.playCount / (double) Math.max(days, 1));
        summary.put("featureCoverage", metrics.playCount == 0 ? 0.0 : metrics.featureSamples / (double) metrics.playCount);
        summary.put("dataSource", dataSource);
        summary.put("approximateUniqueSongs", approximateUniqueSongs);
        summary.put("calculationVersion", UserMusicSummaryService.CALCULATION_VERSION);
        return summary;
    }

    private ListenMetrics calculateRawMetrics(List<ListenHistory> histories) {
        ListenMetrics metrics = new ListenMetrics();
        if (histories == null || histories.isEmpty()) {
            return metrics;
        }

        metrics.playCount = histories.size();
        Set<Long> uniqueSongIds = histories.stream()
                .map(ListenHistory::getSongId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        metrics.uniqueSongCount = uniqueSongIds.size();

        Map<Long, Song> songMap = loadSongMap(uniqueSongIds);
        long totalDuration = 0L;
        long featureSamples = 0L;
        for (ListenHistory history : histories) {
            if (history.getDuration() != null && history.getDuration() > 0) {
                totalDuration += history.getDuration();
            } else {
                Song song = songMap.get(history.getSongId());
                if (song != null && song.getDuration() != null) {
                    totalDuration += song.getDuration();
                }
            }

            Song song = songMap.get(history.getSongId());
            if (song != null && (song.getValence() != null || song.getEnergy() != null)) {
                featureSamples++;
            }
        }
        metrics.playSeconds = totalDuration;
        metrics.featureSamples = featureSamples;
        return metrics;
    }

    private List<ListenHistory> loadHistories(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<ListenHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ListenHistory::getUserId, userId)
                .eq(ListenHistory::getDeleted, 0)
                .ge(ListenHistory::getCreateTime, startTime);
        if (endTime != null) {
            wrapper.lt(ListenHistory::getCreateTime, endTime);
        }
        return listenHistoryMapper.selectList(wrapper);
    }

    private boolean hasDailyCoverage(List<UserMusicDailySummary> summaries, LocalDate startDate, LocalDate endDate) {
        if (summaries == null || summaries.isEmpty() || endDate.isBefore(startDate)) {
            return false;
        }
        Set<LocalDate> coveredDates = summaries.stream()
                .map(UserMusicDailySummary::getStatDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (!coveredDates.contains(cursor)) {
                return false;
            }
            cursor = cursor.plusDays(1);
        }
        return true;
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

    private Map<String, Integer> buildMonthlyDistributionFromHistories(List<ListenHistory> histories) {
        Map<String, Integer> monthlyDistribution = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            final int currentMonth = month;
            int count = (int) histories.stream()
                    .filter(h -> h.getCreateTime() != null && h.getCreateTime().getMonthValue() == currentMonth)
                    .count();
            monthlyDistribution.put(String.format("%d月", month), count);
        }
        return monthlyDistribution;
    }

    private Map<String, Integer> buildMonthlyDistributionFromSummaries(List<UserMusicMonthlySummary> summaries) {
        Map<String, Integer> monthlyDistribution = new HashMap<>();
        for (int month = 1; month <= 12; month++) {
            monthlyDistribution.put(String.format("%d月", month), 0);
        }
        for (UserMusicMonthlySummary summary : summaries) {
            if (summary.getStatMonth() == null) {
                continue;
            }
            monthlyDistribution.put(String.format("%d月", summary.getStatMonth().getMonthValue()),
                    safeInt(summary.getPlayCount()));
        }
        return monthlyDistribution;
    }

    private double calculateFeatureCoverage(List<UserMusicMonthlySummary> summaries) {
        long playCount = 0L;
        double weightedCoverage = 0.0;
        for (UserMusicMonthlySummary summary : summaries) {
            int monthPlays = safeInt(summary.getPlayCount());
            playCount += monthPlays;
            weightedCoverage += monthPlays * safeDouble(summary.getFeatureCoverage());
        }
        return playCount == 0 ? 0.0 : weightedCoverage / playCount;
    }

    private LocalDateTime dataUntilForYear(Integer year) {
        LocalDateTime endOfYear = LocalDate.of(year + 1, 1, 1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        return endOfYear.isBefore(now) ? endOfYear.minusSeconds(1) : now;
    }

    private String todayCacheKey(String scope, Long userId) {
        return CACHE_PREFIX + scope + ":" + userId + ":" + LocalDate.now();
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

    private Map<Long, Song> loadSongMap(Collection<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Song> songs = songMapper.selectBatchIds(new ArrayList<>(songIds));
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Song> songMap = new LinkedHashMap<>();
        for (Song song : songs) {
            if (song != null && song.getId() != null) {
                songMap.put(song.getId(), song);
            }
        }
        return songMap;
    }

    private List<Long> resolveArtistIds(Song song) {
        if (song == null) {
            return Collections.emptyList();
        }
        List<Long> artistIds = new ArrayList<>();
        if (song.getArtistId() != null) {
            artistIds.add(song.getArtistId());
        }
        if (song.getArtistIds() == null || song.getArtistIds().trim().isEmpty()) {
            return artistIds;
        }
        for (String value : song.getArtistIds().split(",")) {
            String trimmed = value == null ? null : value.trim();
            if (trimmed == null || trimmed.isEmpty()) {
                continue;
            }
            try {
                Long artistId = Long.parseLong(trimmed);
                if (!artistIds.contains(artistId)) {
                    artistIds.add(artistId);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid artist id in yearly report: {}", value);
            }
        }
        return artistIds;
    }

    private String normalizeArtistNames(String artistNames) {
        if (artistNames == null) {
            return null;
        }
        String trimmed = artistNames.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class ArtistStat {
        private final Long artistId;
        private String artistName;
        private int playCount;

        private ArtistStat(Long artistId, String artistName) {
            this.artistId = artistId;
            this.artistName = artistName;
            this.playCount = 0;
        }
    }

    private static class ListenMetrics {
        private int playCount;
        private int uniqueSongCount;
        private long playSeconds;
        private long featureSamples;
    }
}
