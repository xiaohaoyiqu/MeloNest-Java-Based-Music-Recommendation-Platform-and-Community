package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.AudioFeatureRecommendService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.SongService;
import com.haoran.music.service.SongLikeService;
import com.haoran.music.vo.recommend.RecommendVO;
import com.haoran.music.vo.recommend.RecommendedSongVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

   
             
  
                      
   
@Slf4j
@Service
public class AudioFeatureRecommendServiceImpl implements AudioFeatureRecommendService {

    @Autowired
    private SongService songService;

    @Autowired
    private SongLikeService songLikeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

             
    private static final String SCENARIO_CACHE_KEY = "scenario:presets";

              
    private static final String SIMILARITY_CACHE_PREFIX = "audio:similarity:";

    private static final Set<String> SCENARIO_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "play_count", "favorite_count", "create_time", "danceability", "energy", "valence", "tempo", "acousticness"
    ));

    @Override
    public RecommendVO recommendByScenario(Long userId, String scenarioCode, Integer limit) {
        RecommendVO result = new RecommendVO();
        result.setScenario(scenarioCode);
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                 
        Map<String, Object> scenario = getScenarioByCode(scenarioCode);
        if (scenario == null) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                 
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, name, artist_names, album_name, duration, cover, ");
        sql.append("uploader_id, ");
        sql.append("danceability, energy, valence, tempo, acousticness ");
        sql.append("FROM song WHERE status = 1 AND deleted = 0 ");

        List<Object> params = new ArrayList<>();

                   
        BigDecimal minEnergy = (BigDecimal) scenario.get("min_energy");
        BigDecimal maxEnergy = (BigDecimal) scenario.get("max_energy");
        if (minEnergy != null && maxEnergy != null) {
            sql.append("AND energy BETWEEN ? AND ? ");
            params.add(minEnergy);
            params.add(maxEnergy);
        }

                         
        BigDecimal minDanceability = (BigDecimal) scenario.get("min_danceability");
        BigDecimal maxDanceability = (BigDecimal) scenario.get("max_danceability");
        if (minDanceability != null && maxDanceability != null) {
            sql.append("AND danceability BETWEEN ? AND ? ");
            params.add(minDanceability);
            params.add(maxDanceability);
        }

                    
        BigDecimal minValence = (BigDecimal) scenario.get("min_valence");
        BigDecimal maxValence = (BigDecimal) scenario.get("max_valence");
        if (minValence != null && maxValence != null) {
            sql.append("AND valence BETWEEN ? AND ? ");
            params.add(minValence);
            params.add(maxValence);
        }

                  
        BigDecimal minTempo = (BigDecimal) scenario.get("min_tempo");
        BigDecimal maxTempo = (BigDecimal) scenario.get("max_tempo");
        if (minTempo != null && maxTempo != null) {
            sql.append("AND tempo BETWEEN ? AND ? ");
            params.add(minTempo);
            params.add(maxTempo);
        }

                             
        BigDecimal minInstrumental = (BigDecimal) scenario.get("min_instrumentalness");
        BigDecimal maxInstrumental = (BigDecimal) scenario.get("max_instrumentalness");
        if (minInstrumental != null && maxInstrumental != null) {
            sql.append("AND instrumentalness BETWEEN ? AND ? ");
            params.add(minInstrumental);
            params.add(maxInstrumental);
        }

             
        String sortField = safeScenarioSortField((String) scenario.get("sort_field"));
        String sortOrder = safeSortOrder((String) scenario.get("sort_order"));
        sql.append("ORDER BY ").append(sortField).append(" ").append(sortOrder);

        sql.append(" LIMIT ?");
        params.add(candidateLimit(safeLimit));

               
        List<Map<String, Object>> songs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

               
        List<RecommendedSongVO> songVOs = mapPublicSongRows(songs, safeLimit);

                 
        String scenarioName = (String) scenario.get("name");
        songVOs.forEach(vo -> vo.setReason("根据【" + scenarioName + "】场景推荐"));

        result.setSongs((List) songVOs);

        return result;
    }

    @Override
    public RecommendVO recommendByMood(Long userId, Double valence, Double energy, Integer limit) {
        RecommendVO result = new RecommendVO();
        result.setMood(valence);
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            result.setSongs(new ArrayList<>());
            return result;
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, name, artist_names, album_name, duration, cover, ");
        sql.append("uploader_id, ");
        sql.append("danceability, energy, valence, tempo ");
        sql.append("FROM song WHERE status = 1 AND deleted = 0 ");
        sql.append("AND valence IS NOT NULL ");

        List<Object> params = new ArrayList<>();

                        
        double valenceMin = Math.max(0, valence - 0.15);
        double valenceMax = Math.min(1, valence + 0.15);
        sql.append("AND valence BETWEEN ? AND ? ");
        params.add(valenceMin);
        params.add(valenceMax);

                      
        if (energy != null) {
            double energyMin = Math.max(0, energy - 0.2);
            double energyMax = Math.min(1, energy + 0.2);
            sql.append("AND energy BETWEEN ? AND ? ");
            params.add(energyMin);
            params.add(energyMax);
        }

                 
        sql.append("ORDER BY ABS(valence - ?) ");
        params.add(valence);

        if (energy != null) {
            sql.append("+ ABS(energy - ?) ");
            params.add(energy);
        }

        sql.append("ASC, play_count DESC LIMIT ?");
        params.add(candidateLimit(safeLimit));

        List<Map<String, Object>> songs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<RecommendedSongVO> songVOs = mapPublicSongRows(songs, safeLimit);

                 
        String moodDesc = getMoodDescription(valence);
        songVOs.forEach(vo -> vo.setReason("根据【" + moodDesc + "】情绪推荐"));

        result.setSongs((List) songVOs);

        return result;
    }

    @Override
    public RecommendVO recommendByAudioFeatures(Long userId, Long songId, Integer limit) {
        RecommendVO result = new RecommendVO();
        result.setBaseSongId(songId);
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                      
        Map<String, Object> features = getSongAudioFeatures(songId);
        if (features == null || features.isEmpty()) {
            result.setSongs(new ArrayList<>());
            return result;
        }

        BigDecimal refDanceability = (BigDecimal) features.get("danceability");
        BigDecimal refEnergy = (BigDecimal) features.get("energy");
        BigDecimal refValence = (BigDecimal) features.get("valence");
        BigDecimal refTempo = (BigDecimal) features.get("tempo");
        BigDecimal refAcousticness = (BigDecimal) features.get("acousticness");

                   
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, name, artist_names, album_name, duration, cover, ");
        sql.append("uploader_id, ");
        sql.append("danceability, energy, valence, tempo, acousticness, ");
        sql.append("ABS(COALESCE(danceability, ?) - ?) * 2 + ");
        sql.append("ABS(COALESCE(energy, ?) - ?) * 2 + ");
        sql.append("ABS(COALESCE(valence, ?) - ?) * 3 + ");
        sql.append("ABS(COALESCE(tempo, ?) - ?) / 100 + ");
        sql.append("ABS(COALESCE(acousticness, ?) - ?) ");
        sql.append("AS similarity_score ");
        sql.append("FROM song WHERE id != ? AND status = 1 AND deleted = 0 ");
        sql.append("ORDER BY similarity_score ASC, play_count DESC ");
        sql.append("LIMIT ?");

        List<Map<String, Object>> songs = jdbcTemplate.queryForList(sql.toString(),
                refDanceability != null ? refDanceability : 0.5, refDanceability != null ? refDanceability : 0.5,
                refEnergy != null ? refEnergy : 0.5, refEnergy != null ? refEnergy : 0.5,
                refValence != null ? refValence : 0.5, refValence != null ? refValence : 0.5,
                refTempo != null ? refTempo : 120, refTempo != null ? refTempo : 120,
                refAcousticness != null ? refAcousticness : 0.25, refAcousticness != null ? refAcousticness : 0.25,
                songId, candidateLimit(safeLimit));           

                  
        List<RecommendedSongVO> songVOs = mapPublicSongRows(songs, safeLimit);

        songVOs.forEach(vo -> vo.setReason("根据歌曲音频特征相似度推荐"));

        result.setSongs((List) songVOs);

        return result;
    }

    @Override
    public List<Map<String, Object>> getScenarios() {
        String sql = "SELECT code, name, description, min_energy, max_energy, " +
                "min_danceability, max_danceability, min_valence, max_valence " +
                "FROM scenario_preset WHERE status = 1 ORDER BY id";

        return jdbcTemplate.queryForList(sql);
    }

    @Override
    public Map<String, Object> getSongAudioFeatures(Long songId) {
        String sql = "SELECT danceability, energy, valence, tempo, acousticness, " +
                "instrumentalness, speechiness, liveness, audio_key, loudness, mode, time_signature, uploader_id " +
                "FROM song WHERE id = ? AND status = 1 AND deleted = 0";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, songId);
        List<Map<String, Object>> publicRows = filterPublicUploaderRows(results);
        return publicRows.isEmpty() ? null : stripUploaderId(publicRows.get(0));
    }

       
                           
       
    @Override
    public List<Map<String, Object>> batchGetAudioFeatures(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return new ArrayList<>();
        }

                         
        String placeholders = songIds.stream()
                .map(id -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT id, danceability, energy, valence, tempo, acousticness " +
                ", uploader_id FROM song WHERE id IN (" + placeholders + ") AND status = 1 AND deleted = 0";

        return filterPublicUploaderRows(jdbcTemplate.queryForList(sql, songIds.toArray())).stream()
                .map(this::stripUploaderId)
                .collect(Collectors.toList());
    }

    @Override
    public Double calculateSimilarity(Long songId1, Long songId2) {
               
        String cacheKey = SIMILARITY_CACHE_PREFIX + musicIntelligenceCacheService.recommendVersionSegment()
                + Math.min(songId1, songId2) + ":" + Math.max(songId1, songId2);
        Object cachedObj = redisUtils.get(cacheKey);
        Double cached = cachedObj instanceof Double ? (Double) cachedObj : null;
        if (cached != null) {
            return cached;
        }

        Map<String, Object> features1 = getSongAudioFeatures(songId1);
        Map<String, Object> features2 = getSongAudioFeatures(songId2);

        if (features1 == null || features2 == null) {
            return 0.0;
        }

                    
        double diff = 0;
        int weightSum = 0;

                             
        diff += getFeatureDiff(features1, features2, "danceability", 0.5) * 2;
        weightSum += 2;

                       
        diff += getFeatureDiff(features1, features2, "energy", 0.5) * 2;
        weightSum += 2;

                        
        diff += getFeatureDiff(features1, features2, "valence", 0.5) * 3;
        weightSum += 3;

                          
        diff += getFeatureDiff(features1, features2, "tempo", 120) / 120;
        weightSum += 1;

                            
        double similarity = Math.max(0, 1 - (diff / weightSum));

               
        redisUtils.set(cacheKey, similarity, 3600, java.util.concurrent.TimeUnit.SECONDS);
        return similarity;
    }

    @Override
    public RecommendVO recommendByMoodAndPreference(Long userId, Double valence, Double energy, Integer limit) {
        RecommendVO result = new RecommendVO();
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            result.setSongs(new ArrayList<>());
            return result;
        }

                   
        List<String> preferredGenres = getUserPreferredGenres(userId);
        List<Object> params = new ArrayList<>();

               
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, name, artist_names, album_name, duration, cover, ");
        sql.append("uploader_id, ");
        sql.append("danceability, energy, valence, tempo, main_genre ");
        sql.append("FROM song WHERE status = 1 AND deleted = 0 ");
        if (!preferredGenres.isEmpty()) {
            String placeholders = preferredGenres.stream()
                    .map(genre -> "?")
                    .collect(Collectors.joining(","));
            sql.append("AND main_genre IN (").append(placeholders).append(") ");
            params.addAll(preferredGenres);
        }

               
        if (valence != null) {
            double vMin = Math.max(0, valence - 0.2);
            double vMax = Math.min(1, valence + 0.2);
            sql.append("AND valence BETWEEN ? AND ? ");
            params.add(vMin);
            params.add(vMax);
        }

               
        if (energy != null) {
            double eMin = Math.max(0, energy - 0.2);
            double eMax = Math.min(1, energy + 0.2);
            sql.append("AND energy BETWEEN ? AND ? ");
            params.add(eMin);
            params.add(eMax);
        }

                    
        sql.append("ORDER BY ");
        if (valence != null) {
            sql.append("ABS(valence - ?), ");
            params.add(valence);
        }
        if (energy != null) {
            sql.append("ABS(energy - ?), ");
            params.add(energy);
        }
        sql.append("play_count DESC LIMIT ?");
        params.add(candidateLimit(safeLimit));

        List<Map<String, Object>> songs = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<RecommendedSongVO> songVOs = mapPublicSongRows(songs, safeLimit);

        songVOs.forEach(vo -> vo.setReason("根据你的喜好和当前情绪推荐"));

        result.setSongs((List) songVOs);
        return result;
    }

    @Override
    public Map<String, Object> getUserMoodAnalysis(Long userId) {
        Map<String, Object> analysis = new HashMap<>();

                        
        String sql = "SELECT AVG(s.valence) as avg_valence, " +
                "AVG(s.energy) as avg_energy, " +
                "AVG(s.danceability) as avg_danceability, " +
                "AVG(s.tempo) as avg_tempo, " +
                "COUNT(*) as listen_count " +
                "FROM listen_history lh " +
                "INNER JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.deleted = 0 AND s.valence IS NOT NULL";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);

        if (!results.isEmpty()) {
            Map<String, Object> row = results.get(0);
            analysis.putAll(row);

                     
            BigDecimal avgValence = (BigDecimal) row.get("avg_valence");
            if (avgValence != null) {
                if (avgValence.compareTo(new BigDecimal("0.7")) >= 0) {
                    analysis.put("mood_tendency", "积极乐观");
                } else if (avgValence.compareTo(new BigDecimal("0.4")) <= 0) {
                    analysis.put("mood_tendency", "低沉忧郁");
                } else {
                    analysis.put("mood_tendency", "平和中性");
                }
            }

                     
            BigDecimal avgEnergy = (BigDecimal) row.get("avg_energy");
            if (avgEnergy != null) {
                if (avgEnergy.compareTo(new BigDecimal("0.7")) >= 0) {
                    analysis.put("energy_tendency", "高能量");
                } else if (avgEnergy.compareTo(new BigDecimal("0.4")) <= 0) {
                    analysis.put("energy_tendency", "低能量");
                } else {
                    analysis.put("energy_tendency", "中等能量");
                }
            }
        }

        return analysis;
    }

    @Override
    public RecommendVO recommendByBpmRange(Long userId, Integer minBpm, Integer maxBpm, Integer limit) {
        RecommendVO result = new RecommendVO();
        result.setBpmRange(minBpm + "-" + maxBpm);
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            result.setSongs(new ArrayList<>());
            return result;
        }

        String sql = "SELECT id, name, artist_names, album_name, duration, cover, uploader_id, tempo " +
                "FROM song WHERE status = 1 AND deleted = 0 " +
                "AND tempo BETWEEN ? AND ? " +
                "ORDER BY ABS(tempo - ?) ASC, play_count DESC LIMIT ?";

        List<Map<String, Object>> songs = jdbcTemplate.queryForList(sql, minBpm, maxBpm,
                (minBpm + maxBpm) / 2.0, candidateLimit(safeLimit));

        List<RecommendedSongVO> songVOs = mapPublicSongRows(songs, safeLimit);

        songVOs.forEach(vo -> vo.setReason("根据BPM范围(" + minBpm + "-" + maxBpm + ")推荐"));

        result.setSongs((List) songVOs);
        return result;
    }

                                                       

    private Map<String, Object> getScenarioByCode(String code) {
        String sql = "SELECT * FROM scenario_preset WHERE code = ? AND status = 1";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, code);
        return results.isEmpty() ? null : results.get(0);
    }

    private RecommendedSongVO mapToRecommendedSongVO(Map<String, Object> row) {
        RecommendedSongVO vo = new RecommendedSongVO();
        vo.setId(((Number) row.get("id")).longValue());
        vo.setName((String) row.get("name"));
        vo.setArtistNames((String) row.get("artist_names"));
        vo.setAlbumName((String) row.get("album_name"));
        vo.setDuration(row.get("duration") != null ? ((Number) row.get("duration")).intValue() : null);
        vo.setCover((String) row.get("cover"));

               
        Object danceability = row.get("danceability");
        if (danceability instanceof BigDecimal) {
            vo.setDanceability(((BigDecimal) danceability).doubleValue());
        }
        Object energy = row.get("energy");
        if (energy instanceof BigDecimal) {
            vo.setEnergy(((BigDecimal) energy).doubleValue());
        }
        Object valence = row.get("valence");
        if (valence instanceof BigDecimal) {
            vo.setValence(((BigDecimal) valence).doubleValue());
        }
        Object tempo = row.get("tempo");
        if (tempo instanceof BigDecimal) {
            vo.setTempo(((BigDecimal) tempo).doubleValue());
        }

        return vo;
    }

    private List<RecommendedSongVO> mapPublicSongRows(List<Map<String, Object>> rows, int limit) {
        if (rows == null || rows.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }
        return filterPublicUploaderRows(rows).stream()
                .limit(limit)
                .map(this::mapToRecommendedSongVO)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> filterPublicUploaderRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
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

    private String safeScenarioSortField(String sortField) {
        if (sortField == null || !SCENARIO_SORT_FIELDS.contains(sortField)) {
            return "play_count";
        }
        return sortField;
    }

    private String safeSortOrder(String sortOrder) {
        return "ASC".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
    }

    private double getFeatureDiff(Map<String, Object> f1, Map<String, Object> f2, String key, double defaultValue) {
        Object v1 = f1.get(key);
        Object v2 = f2.get(key);

        double d1 = v1 instanceof BigDecimal ? ((BigDecimal) v1).doubleValue() : defaultValue;
        double d2 = v2 instanceof BigDecimal ? ((BigDecimal) v2).doubleValue() : defaultValue;

        return Math.abs(d1 - d2);
    }

    private String getMoodDescription(double valence) {
        if (valence >= 0.8) return "非常开心";
        if (valence >= 0.6) return "开心愉快";
        if (valence >= 0.4) return "平静平和";
        if (valence >= 0.2) return "略带忧郁";
        return "低落伤感";
    }

    private List<String> getUserPreferredGenres(Long userId) {
        String sql = "SELECT s.main_genre, s.uploader_id " +
                "FROM listen_history lh " +
                "INNER JOIN song s ON lh.song_id = s.id " +
                "WHERE lh.user_id = ? AND lh.deleted = 0 AND s.status = 1 AND s.deleted = 0 " +
                "AND s.main_genre IS NOT NULL " +
                "ORDER BY lh.create_time DESC " +
                "LIMIT 200";

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, userId);
        Map<String, Integer> genreCount = new HashMap<>();
        for (Map<String, Object> row : filterPublicUploaderRows(results)) {
            String genre = (String) row.get("main_genre");
            if (genre != null) {
                genreCount.merge(genre, 1, Integer::sum);
            }
        }
        return genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
