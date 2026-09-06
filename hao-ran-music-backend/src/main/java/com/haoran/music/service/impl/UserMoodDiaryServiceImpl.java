package com.haoran.music.service.impl;

import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.UserMoodDiaryService;
import com.haoran.music.vo.audio.MoodDiaryEntry;
import com.haoran.music.vo.audio.MoodDiaryStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

   
             
                        
  
                      
   
@Slf4j
@Service
public class UserMoodDiaryServiceImpl implements UserMoodDiaryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SongMapper songMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    private static final String CACHE_PREFIX = "audio:mood:";
    private static final long SHORT_CACHE_MINUTES = 10L;

    @Override
    public MoodDiaryEntry getMoodDiary(Long userId, LocalDate date) {
        return cacheValue(CACHE_PREFIX + "diary:" + userId + ":" + date,
                () -> loadMoodDiary(userId, date), SHORT_CACHE_MINUTES, MoodDiaryEntry.class);
    }

    private MoodDiaryEntry loadMoodDiary(Long userId, LocalDate date) {
        String sql = "SELECT lh.*, s.name as song_name, s.artist_names, s.valence, s.energy, s.danceability " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                "ORDER BY lh.create_time DESC";

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, userId,
                startOfDay(date), endExclusive(date));

        if (ObjectUtils.isEmpty(records)) {
            return createEmptyDiary(date);
        }

        return analyzeMoodFromRecords(date, records);
    }

    @Override
    public List<MoodDiaryEntry> getMoodDiaryTimeline(Long userId, LocalDate startDate, LocalDate endDate) {
        if (userId == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("用户和日期范围不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > 366) {
            throw new IllegalArgumentException("情绪日记日期范围不能超过366天");
        }
        return cacheList(CACHE_PREFIX + "timeline:" + userId + ":" + startDate + ":" + endDate,
                () -> loadMoodDiaryTimeline(userId, startDate, endDate), SHORT_CACHE_MINUTES);
    }

    private List<MoodDiaryEntry> loadMoodDiaryTimeline(Long userId, LocalDate startDate, LocalDate endDate) {
        String sql = "SELECT DATE(lh.create_time) as listen_date, " +
                "COUNT(*) as listen_count, " +
                "AVG(s.valence) as avg_valence, " +
                "AVG(s.energy) as avg_energy, " +
                "AVG(s.danceability) as avg_danceability, " +
                "COUNT(DISTINCT lh.song_id) as unique_songs " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                "GROUP BY DATE(lh.create_time) " +
                "ORDER BY listen_date DESC";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId,
                startOfDay(startDate), endExclusive(endDate));

        return results.stream().map(row -> {
            Object dateObj = row.get("listen_date");
            LocalDate listenDate;
            listenDate = toLocalDate(dateObj);
            MoodDiaryEntry entry = new MoodDiaryEntry();
            entry.setDate(listenDate);
            entry.setPlayCount(((Number) row.getOrDefault("listen_count", 0)).intValue());
            entry.setAvgValence(((Number) row.getOrDefault("avg_valence", 0.5)).doubleValue());
            entry.setAvgEnergy(((Number) row.getOrDefault("avg_energy", 0.5)).doubleValue());
            entry.setAvgDanceability(((Number) row.getOrDefault("avg_danceability", 0.5)).doubleValue());
            entry.setUniqueSongs(((Number) row.getOrDefault("unique_songs", 0)).intValue());
            return entry;
        }).collect(Collectors.toList());
    }

    @Override
    public MoodDiaryStats getMoodStats(Long userId, Integer days) {
        int safeDays = safeDays(days, 30, 366);
        return cacheValue(CACHE_PREFIX + "stats:" + userId + ":" + safeDays + ":" + LocalDate.now(),
                () -> loadMoodStats(userId, safeDays), SHORT_CACHE_MINUTES, MoodDiaryStats.class);
    }

    private MoodDiaryStats loadMoodStats(Long userId, int safeDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeDays);

        String sql = "SELECT " +
                "COUNT(*) as total_listens, " +
                "COUNT(DISTINCT song_id) as unique_songs, " +
                "COUNT(DISTINCT DATE(create_time)) as active_days, " +
                "AVG(s.valence) as avg_valence, " +
                "AVG(s.energy) as avg_energy, " +
                "AVG(s.danceability) as avg_danceability, " +
                "MAX(s.valence) as max_valence, " +
                "MIN(s.valence) as min_valence " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ?";

        Map<String, Object> stats = jdbcTemplate.queryForMap(sql, userId,
                startOfDay(startDate), endExclusive(endDate));

        MoodDiaryStats diaryStats = new MoodDiaryStats();
        diaryStats.setDays(safeDays);
        diaryStats.setTotalListens(((Number) stats.getOrDefault("total_listens", 0)).intValue());
        diaryStats.setUniqueSongs(((Number) stats.getOrDefault("unique_songs", 0)).intValue());
        diaryStats.setActiveDays(((Number) stats.getOrDefault("active_days", 0)).intValue());
        diaryStats.setAvgValence(((Number) stats.getOrDefault("avg_valence", 0.5)).doubleValue());
        diaryStats.setAvgEnergy(((Number) stats.getOrDefault("avg_energy", 0.5)).doubleValue());
        diaryStats.setAvgDanceability(((Number) stats.getOrDefault("avg_danceability", 0.5)).doubleValue());
        diaryStats.setMaxValence(((Number) stats.getOrDefault("max_valence", 0.5)).doubleValue());
        diaryStats.setMinValence(((Number) stats.getOrDefault("min_valence", 0.5)).doubleValue());

                 
        diaryStats.setMoodDistribution(calculateMoodDistribution(userId, startDate, endDate));

                  
        diaryStats.setTopSongs(getTopSongsInPeriod(userId, startDate, endDate, 5));

                 
        diaryStats.setDominantMood(getMoodLabel(diaryStats.getAvgValence()));

        return diaryStats;
    }

    @Override
    public List<Map<String, Object>> getMoodCurve(Long userId, Integer days) {
        int safeDays = safeDays(days, 30, 366);
        return cacheMapList(CACHE_PREFIX + "curve:" + userId + ":" + safeDays + ":" + LocalDate.now(),
                () -> loadMoodCurve(userId, safeDays), SHORT_CACHE_MINUTES);
    }

    private List<Map<String, Object>> loadMoodCurve(Long userId, int safeDays) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(safeDays);

        String sql = "SELECT DATE(lh.create_time) as date, " +
                "AVG(s.valence) as valence, " +
                "AVG(s.energy) as energy, " +
                "COUNT(*) as count " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                "GROUP BY DATE(lh.create_time) " +
                "ORDER BY date ASC";

        List<Map<String, Object>> curveData = jdbcTemplate.queryForList(sql, userId,
                startOfDay(startDate), endExclusive(endDate));

                 
        Map<LocalDate, Map<String, Object>> curveMap = new LinkedHashMap<>();
        for (Map<String, Object> row : curveData) {
            curveMap.put(toLocalDate(row.get("date")), row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            Map<String, Object> data = curveMap.get(d);
            if (data != null) {
                Map<String, Object> point = new HashMap<>();
                point.put("date", d.toString());
                point.put("valence", data.get("valence"));
                point.put("energy", data.get("energy"));
                result.add(point);
            } else {
                                  
                Map<String, Object> point = new HashMap<>();
                point.put("date", d.toString());
                point.put("valence", 0.5);
                point.put("energy", 0.5);
                result.add(point);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> analyzeMoodTrend(Long userId) {
        return cacheObjectMap(CACHE_PREFIX + "trend:" + userId + ":" + LocalDate.now(),
                () -> loadMoodTrend(userId), SHORT_CACHE_MINUTES);
    }

    private Map<String, Object> loadMoodTrend(Long userId) {
        LocalDate now = LocalDate.now();

                          
        List<MoodDiaryEntry> recentWeek = getMoodDiaryTimeline(userId, now.minusDays(7), now);
        List<MoodDiaryEntry> previousWeek = getMoodDiaryTimeline(userId, now.minusDays(14), now.minusDays(8));

        double recentAvgValence = recentWeek.stream()
                .mapToDouble(MoodDiaryEntry::getAvgValence)
                .average()
                .orElse(0.5);
        double previousAvgValence = previousWeek.stream()
                .mapToDouble(MoodDiaryEntry::getAvgValence)
                .average()
                .orElse(0.5);

        double recentAvgEnergy = recentWeek.stream()
                .mapToDouble(MoodDiaryEntry::getAvgEnergy)
                .average()
                .orElse(0.5);
        double previousAvgEnergy = previousWeek.stream()
                .mapToDouble(MoodDiaryEntry::getAvgEnergy)
                .average()
                .orElse(0.5);

        Map<String, Object> trend = new HashMap<>();
        trend.put("currentMood", getMoodLabel(recentAvgValence));
        trend.put("currentEnergy", getEnergyLabel(recentAvgEnergy));
        trend.put("valenceChange", recentAvgValence - previousAvgValence);
        trend.put("energyChange", recentAvgEnergy - previousAvgEnergy);
        trend.put("trendDirection", recentAvgValence > previousAvgValence ? "up" :
                (recentAvgValence < previousAvgValence ? "down" : "stable"));
        trend.put("recommendation", generateMoodRecommendation(recentAvgValence, recentAvgEnergy));

        return trend;
    }

    @Override
    public List<Long> getMoodBasedRecommendation(Long userId, Integer limit) {
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            return Collections.emptyList();
        }
        return cacheLongList(CACHE_PREFIX + "recommend:" + userId + ":" + safeLimit + ":" + LocalDate.now(),
                () -> loadMoodBasedRecommendation(userId, safeLimit), SHORT_CACHE_MINUTES);
    }

    private List<Long> loadMoodBasedRecommendation(Long userId, int safeLimit) {
                       
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        String sql = "SELECT AVG(s.valence) as avg_valence, AVG(s.energy) as avg_energy, " +
                "AVG(s.danceability) as avg_danceability, " +
                "s.main_genre " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? " +
                "AND s.status = 1 AND s.deleted = 0 " +
                "GROUP BY s.main_genre " +
                "ORDER BY COUNT(*) DESC " +
                "LIMIT 1";

        List<Map<String, Object>> preferences = jdbcTemplate.queryForList(sql, userId, startOfDay(sevenDaysAgo));

        if (ObjectUtils.isEmpty(preferences)) {
                         
            return getHotSongs(safeLimit);
        }

        Map<String, Object> pref = preferences.get(0);
        double avgValence = ((Number) pref.getOrDefault("avg_valence", 0.5)).doubleValue();
        double avgEnergy = ((Number) pref.getOrDefault("avg_energy", 0.5)).doubleValue();
        String favoriteGenre = (String) pref.get("main_genre");

                             
        String recommendSql = "SELECT DISTINCT s.id, s.uploader_id " +
                "FROM song s " +
                "WHERE s.status = 1 AND s.deleted = 0 " +
                "AND s.valence BETWEEN ? AND ? " +
                "AND s.energy BETWEEN ? AND ? ";

        List<Object> params = new ArrayList<>();
        params.add(Math.max(0, avgValence - 0.2));
        params.add(Math.min(1, avgValence + 0.2));
        params.add(Math.max(0, avgEnergy - 0.2));
        params.add(Math.min(1, avgEnergy + 0.2));

        if (favoriteGenre != null) {
            recommendSql += "AND s.main_genre = ? ";
            params.add(favoriteGenre);
        }

        recommendSql += "AND s.id NOT IN (SELECT song_id FROM listen_history WHERE user_id = ?) " +
                "ORDER BY s.play_count DESC " +
                "LIMIT ?";
        params.add(userId);
        params.add(candidateLimit(safeLimit));

        List<Map<String, Object>> results = jdbcTemplate.queryForList(recommendSql, params.toArray());

        return filterPublicUploaderRows(results).stream()
                .limit(safeLimit)
                .map(row -> ((Number) row.get("id")).longValue())
                .collect(Collectors.toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T cacheValue(String key, Supplier<T> supplier, long ttlMinutes, Class<T> clazz) {
        try {
            return CacheHelper.getOrLoad(redisUtils, key, supplier, ttlMinutes, TimeUnit.MINUTES, clazz);
        } catch (Exception e) {
            log.warn("心情服务缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<MoodDiaryEntry> cacheList(String key, Supplier<List<MoodDiaryEntry>> supplier, long ttlMinutes) {
        try {
            return (List<MoodDiaryEntry>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("心情服务缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> cacheMapList(String key, Supplier<List<Map<String, Object>>> supplier,
                                                   long ttlMinutes) {
        try {
            return (List<Map<String, Object>>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("心情服务缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Long> cacheLongList(String key, Supplier<List<Long>> supplier, long ttlMinutes) {
        try {
            return (List<Long>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("心情服务缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> cacheObjectMap(String key, Supplier<Map<String, Object>> supplier, long ttlMinutes) {
        try {
            return (Map<String, Object>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, Map.class);
        } catch (Exception e) {
            log.warn("心情服务缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

       
                
       
    private MoodDiaryEntry analyzeMoodFromRecords(LocalDate date, List<Map<String, Object>> records) {
        MoodDiaryEntry entry = new MoodDiaryEntry();
        entry.setDate(date);
        entry.setPlayCount(records.size());

                  
        double sumValence = 0, sumEnergy = 0, sumDanceability = 0;
        Set<Long> uniqueSongs = new HashSet<>();
        String topSongName = null;
        int topSongCount = 0;
        Map<String, Integer> songCounts = new HashMap<>();

        for (Map<String, Object> record : records) {
            Long songId = ((Number) record.get("song_id")).longValue();
            uniqueSongs.add(songId);

            Number valence = (Number) record.get("valence");
            Number energy = (Number) record.get("energy");
            Number danceability = (Number) record.get("danceability");

            if (valence != null) sumValence += valence.doubleValue();
            if (energy != null) sumEnergy += energy.doubleValue();
            if (danceability != null) sumDanceability += danceability.doubleValue();

                      
            String songName = (String) record.get("song_name");
            if (songName != null) {
                songCounts.merge(songName, 1, Integer::sum);
                if (songCounts.get(songName) > topSongCount) {
                    topSongCount = songCounts.get(songName);
                    topSongName = songName;
                }
            }
        }

        int count = records.size();
        entry.setAvgValence(count > 0 ? sumValence / count : 0.5);
        entry.setAvgEnergy(count > 0 ? sumEnergy / count : 0.5);
        entry.setAvgDanceability(count > 0 ? sumDanceability / count : 0.5);
        entry.setUniqueSongs(uniqueSongs.size());
        entry.setTopSongName(topSongName);

        return entry;
    }

       
                     
       
    private MoodDiaryEntry createEmptyDiary(LocalDate date) {
        MoodDiaryEntry entry = new MoodDiaryEntry();
        entry.setDate(date);
        entry.setPlayCount(0);
        entry.setAvgValence(0.5);
        entry.setAvgEnergy(0.5);
        entry.setAvgDanceability(0.5);
        entry.setUniqueSongs(0);
        return entry;
    }

       
             
       
    private Map<String, Integer> calculateMoodDistribution(Long userId, LocalDate start, LocalDate end) {
        String sql = "SELECT " +
                "SUM(CASE WHEN s.valence >= 0.7 THEN 1 ELSE 0 END) as happy, " +
                "SUM(CASE WHEN s.valence BETWEEN 0.4 AND 0.7 THEN 1 ELSE 0 END) as neutral, " +
                "SUM(CASE WHEN s.valence < 0.4 THEN 1 ELSE 0 END) as sad, " +
                "SUM(CASE WHEN s.energy > 0.7 THEN 1 ELSE 0 END) as energetic, " +
                "SUM(CASE WHEN s.energy <= 0.7 THEN 1 ELSE 0 END) as calm " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ?";

        Map<String, Object> result = jdbcTemplate.queryForMap(sql, userId, startOfDay(start), endExclusive(end));

        Map<String, Integer> distribution = new HashMap<>();
        distribution.put("happy", ((Number) result.getOrDefault("happy", 0)).intValue());
        distribution.put("neutral", ((Number) result.getOrDefault("neutral", 0)).intValue());
        distribution.put("sad", ((Number) result.getOrDefault("sad", 0)).intValue());
        distribution.put("energetic", ((Number) result.getOrDefault("energetic", 0)).intValue());
        distribution.put("calm", ((Number) result.getOrDefault("calm", 0)).intValue());

        return distribution;
    }

       
                 
       
    private List<Map<String, Object>> getTopSongsInPeriod(Long userId, LocalDate start, LocalDate end, int limit) {
        String sql = "SELECT s.id, s.name, s.artist_names, s.cover, COUNT(*) as play_count " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? AND lh.create_time < ? " +
                "GROUP BY s.id " +
                "ORDER BY play_count DESC " +
                "LIMIT ?";

        return jdbcTemplate.queryForList(sql, userId, startOfDay(start), endExclusive(end), limit);
    }

       
             
       
    private List<Long> getHotSongs(Integer limit) {
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, uploader_id FROM song WHERE status = 1 AND deleted = 0 ORDER BY play_count DESC LIMIT ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, candidateLimit(safeLimit));
        return filterPublicUploaderRows(results).stream()
                .limit(safeLimit)
                .map(row -> ((Number) row.get("id")).longValue())
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

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private int safeLimit(Integer limit) {
        return limit == null || limit <= 0 ? 0 : limit;
    }

    private int candidateLimit(int limit) {
        if (limit <= 0) {
            return 0;
        }
        long expanded = Math.max((long) limit * 3L, (long) limit + 10L);
        return (int) Math.min(expanded, 1000L);
    }

    private int safeDays(Integer days, int defaultDays, int maxDays) {
        int value = days == null || days <= 0 ? defaultDays : days;
        return Math.min(value, maxDays);
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    private LocalDateTime endExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof String) {
            return LocalDate.parse((String) value);
        }
        return LocalDate.now();
    }

       
             
       
    private String getMoodLabel(double valence) {
        if (valence >= 0.8) return "非常开心";
        if (valence >= 0.6) return "开心";
        if (valence >= 0.4) return "平静";
        if (valence >= 0.2) return "略低落";
        return "低落";
    }

       
             
       
    private String getEnergyLabel(double energy) {
        if (energy >= 0.7) return "高能量";
        if (energy >= 0.4) return "中等";
        return "低能量";
    }

       
             
       
    private String generateMoodRecommendation(double valence, double energy) {
        if (valence < 0.3 && energy < 0.3) {
            return "心情低落，听听欢快的歌曲提升心情吧";
        } else if (valence > 0.7 && energy > 0.7) {
            return "活力满满！继续保持好心情";
        } else if (energy < 0.3) {
            return "来点动感的歌曲活跃一下";
        } else {
            return "享受当前的音乐时光";
        }
    }
}
