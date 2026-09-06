package com.haoran.music.service.impl;

import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.MusicMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;







@Slf4j
@Service
public class MusicMapServiceImpl implements MusicMapService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisUtils redisUtils;

    private static final String CACHE_PREFIX = "audio:map:";
    private static final long PUBLIC_MAP_CACHE_MINUTES = 10L;
    private static final long USER_MAP_CACHE_MINUTES = 10L;
    private static final int MAX_MAP_LIMIT = 1000;

    @Override
    public List<Map<String, Object>> getMusicMapData(String region, Integer limit) {
        int safeLimit = safeLimit(limit, 500, MAX_MAP_LIMIT);
        String normalizedRegion = region == null ? "all" : region;
        return cacheMapList(CACHE_PREFIX + "public:" + normalizedRegion + ":" + safeLimit,
                () -> loadMusicMapData(normalizedRegion, safeLimit), PUBLIC_MAP_CACHE_MINUTES);
    }

    private List<Map<String, Object>> loadMusicMapData(String region, int safeLimit) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.id, s.name, s.artist_names, s.cover, " +
                "s.energy, s.valence, s.danceability, s.tempo, " +
                "s.main_genre, s.play_count, s.uploader_id " +
                "FROM song s " +
                "WHERE s.status = 1 AND s.deleted = 0 AND s.energy IS NOT NULL AND s.valence IS NOT NULL "
        );


        switch (region) {
            case "energetic":
                sql.append("AND s.energy >= 0.7 ");
                break;
            case "calm":
                sql.append("AND s.energy < 0.5 AND s.valence >= 0.3 AND s.valence <= 0.7 ");
                break;
            case "melancholy":
                sql.append("AND s.valence < 0.4 AND s.energy < 0.6 ");
                break;
            case "exciting":
                sql.append("AND s.valence >= 0.6 AND s.energy >= 0.6 ");
                break;
            case "positive":
                sql.append("AND s.valence >= 0.6 ");
                break;
            default:

                break;
        }

        sql.append("ORDER BY s.play_count DESC LIMIT ?");

        return filterPublicUploaderRows(jdbcTemplate.queryForList(sql.toString(), candidateLimit(safeLimit))).stream()
                .limit(safeLimit)
                .map(this::stripUploaderId)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public Map<String, Object> getMapRegions() {
        Map<String, Object> regions = new LinkedHashMap<>();


        Map<String, Object> energetic = new HashMap<>();
        energetic.put("code", "energetic");
        energetic.put("name", "高能量区");
        energetic.put("description", "充满活力、适合运动的嗨曲");
        energetic.put("energyRange", Arrays.asList(0.7, 1.0));
        energetic.put("valenceRange", Arrays.asList(0.0, 1.0));
        regions.put("energetic", energetic);

        Map<String, Object> calm = new HashMap<>();
        calm.put("code", "calm");
        calm.put("name", "平静区");
        calm.put("description", "舒缓放松、适合休息的音乐");
        calm.put("energyRange", Arrays.asList(0.0, 0.5));
        calm.put("valenceRange", Arrays.asList(0.3, 0.7));
        regions.put("calm", calm);

        Map<String, Object> melancholy = new HashMap<>();
        melancholy.put("code", "melancholy");
        melancholy.put("name", "忧伤区");
        melancholy.put("description", "情感深沉、适合独处时的音乐");
        melancholy.put("energyRange", Arrays.asList(0.0, 0.6));
        melancholy.put("valenceRange", Arrays.asList(0.0, 0.4));
        regions.put("melancholy", melancholy);

        Map<String, Object> exciting = new HashMap<>();
        exciting.put("code", "exciting");
        exciting.put("name", "兴奋区");
        exciting.put("description", "积极向上、让人快乐的乐曲");
        exciting.put("energyRange", Arrays.asList(0.6, 1.0));
        exciting.put("valenceRange", Arrays.asList(0.6, 1.0));
        regions.put("exciting", exciting);

        return regions;
    }

    @Override
    public List<Map<String, Object>> getSongsByRegion(String region, Integer limit) {
        return getMusicMapData(region, limit);
    }

    @Override
    public List<Map<String, Object>> getNearbySongs(Double valence, Double energy, Double radius, Integer limit) {

        double minValence = Math.max(0, valence - radius);
        double maxValence = Math.min(1, valence + radius);
        double minEnergy = Math.max(0, energy - radius);
        double maxEnergy = Math.min(1, energy + radius);

        String sql = "SELECT s.id, s.name, s.artist_names, s.cover, " +
                "s.energy, s.valence, s.danceability, s.main_genre, " +
                "s.uploader_id, ABS(s.energy - ?) + ABS(s.valence - ?) as distance " +
                "FROM song s " +
                "WHERE s.status = 1 AND s.deleted = 0 " +
                "AND s.valence BETWEEN ? AND ? " +
                "AND s.energy BETWEEN ? AND ? " +
                "ORDER BY distance ASC " +
                "LIMIT ?";

        int safeLimit = safeLimit(limit, 20, 100);
        return filterPublicUploaderRows(jdbcTemplate.queryForList(sql, energy, valence, minValence, maxValence,
                minEnergy, maxEnergy, candidateLimit(safeLimit))).stream()
                .limit(safeLimit)
                .map(this::stripUploaderId)
                .collect(java.util.stream.Collectors.toList());
    }

    private List<Map<String, Object>> filterPublicUploaderRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> uploaderIds = rows.stream()
                .map(row -> toLong(row.get("uploader_id")))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds, ids -> userMapper.selectBatchIds(ids));
        return rows.stream()
                .filter(row -> {
                    Long uploaderId = toLong(row.get("uploader_id"));
                    return uploaderId == null || allowedUploaderIds.contains(uploaderId);
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, Object> stripUploaderId(Map<String, Object> row) {
        Map<String, Object> copy = new HashMap<>(row);
        copy.remove("uploader_id");
        return copy;
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private int candidateLimit(int limit) {
        long expanded = Math.max((long) limit * 3L, (long) limit + 10L);
        return (int) Math.min(expanded, 1000L);
    }

    @Override
    public Map<String, Object> getUserExplorationMap(Long userId) {
        return cacheObjectMap(CACHE_PREFIX + "exploration:" + userId + ":" + LocalDate.now(),
                () -> loadUserExplorationMap(userId), USER_MAP_CACHE_MINUTES);
    }

    private Map<String, Object> loadUserExplorationMap(Long userId) {
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        LocalDateTime rangeStart = thirtyDaysAgo.atStartOfDay();


        String sql = "SELECT s.energy, s.valence, s.name, COUNT(*) as play_count " +
                "FROM listen_history lh " +
                "LEFT JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.create_time >= ? " +
                "AND s.energy IS NOT NULL AND s.valence IS NOT NULL " +
                "GROUP BY s.id, s.energy, s.valence, s.name " +
                "ORDER BY play_count DESC";

        List<Map<String, Object>> userSongs = jdbcTemplate.queryForList(sql, userId, rangeStart);


        Map<String, Integer> regionCounts = new HashMap<>();
        regionCounts.put("energetic", 0);
        regionCounts.put("calm", 0);
        regionCounts.put("melancholy", 0);
        regionCounts.put("exciting", 0);

        for (Map<String, Object> song : userSongs) {
            double energy = ((Number) song.get("energy")).doubleValue();
            double valence = ((Number) song.get("valence")).doubleValue();
            int count = ((Number) song.get("play_count")).intValue();

            String region = classifySongRegion(energy, valence);
            regionCounts.merge(region, count, Integer::sum);
        }


        String favoriteRegion = regionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("calm");

        Map<String, Object> exploration = new HashMap<>();
        exploration.put("userSongs", userSongs);
        exploration.put("regionCounts", regionCounts);
        exploration.put("favoriteRegion", favoriteRegion);
        exploration.put("totalUniqueSongs", userSongs.size());

        return exploration;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> cacheMapList(String key, Supplier<List<Map<String, Object>>> supplier,
                                                   long ttlMinutes) {
        try {
            return (List<Map<String, Object>>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("音乐地图缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> cacheObjectMap(String key, Supplier<Map<String, Object>> supplier, long ttlMinutes) {
        try {
            return (Map<String, Object>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, Map.class);
        } catch (Exception e) {
            log.warn("音乐地图缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    private int safeLimit(Integer limit, int defaultLimit, int maxLimit) {
        int value = limit != null && limit > 0 ? limit : defaultLimit;
        return Math.min(value, maxLimit);
    }

    @Override
    public List<Map<String, Object>> getUserPreferredRegions(Long userId) {
        Map<String, Object> exploration = getUserExplorationMap(userId);
        Map<String, Integer> regionCounts = (Map<String, Integer>) exploration.get("regionCounts");


        List<Map<String, Object>> preferredRegions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : regionCounts.entrySet()) {
            Map<String, Object> region = new HashMap<>();
            region.put("code", entry.getKey());
            region.put("visitCount", entry.getValue());


            Map<String, Object> regionInfo = (Map<String, Object>) getMapRegions().get(entry.getKey());
            if (regionInfo != null) {
                region.put("name", regionInfo.get("name"));
                region.put("description", regionInfo.get("description"));
            }

            preferredRegions.add(region);
        }


        preferredRegions.sort((a, b) -> {
            Integer countA = (Integer) a.get("visitCount");
            Integer countB = (Integer) b.get("visitCount");
            return countB.compareTo(countA);
        });

        return preferredRegions;
    }




    private String classifySongRegion(double energy, double valence) {
        if (energy >= 0.6 && valence >= 0.6) {
            return "exciting";                    
        } else if (energy >= 0.7) {
            return "energetic";            
        } else if (valence < 0.4 && energy < 0.6) {
            return "melancholy";              
        } else {
            return "calm";                
        }
    }
}
