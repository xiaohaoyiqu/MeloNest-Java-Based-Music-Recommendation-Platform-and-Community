package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.entity.Playlist;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.PlaylistAudioAnalysisService;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.service.SongService;
import com.haoran.music.vo.audio.PlaylistAudioAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;






@Slf4j
@Service
public class PlaylistAudioAnalysisServiceImpl implements PlaylistAudioAnalysisService {

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private SongService songService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private UserMapper userMapper;

    private static final String PLAYLIST_AUDIO_CACHE_PREFIX = "playlist:audio:";
    private static final int CACHE_HOURS = 24;

    @Override
    public PlaylistAudioAnalysis analyzePlaylist(Long playlistId) {

        String cacheKey = PLAYLIST_AUDIO_CACHE_PREFIX + playlistId;
        Object cachedObj = redisUtils.get(cacheKey);
        PlaylistAudioAnalysis cached = (cachedObj instanceof PlaylistAudioAnalysis) ? (PlaylistAudioAnalysis) cachedObj : null;
        if (cached != null) {
            return cached;
        }

        PlaylistAudioAnalysis analysis = new PlaylistAudioAnalysis();
        analysis.setPlaylistId(playlistId);


        String sql = "SELECT danceability, energy, valence, tempo, acousticness, " +
                "instrumentalness, speechiness, liveness, audio_key, mode, s.uploader_id " +
                "FROM playlist_song ps " +
                "INNER JOIN song s ON ps.song_id = s.id " +
                "WHERE ps.playlist_id = ? AND ps.deleted = 0 AND s.status = 1 AND s.deleted = 0 " +
                "AND s.danceability IS NOT NULL";

        List<Map<String, Object>> features = filterPublicUploaderRows(jdbcTemplate.queryForList(sql, playlistId));

        if (features.isEmpty()) {
            analysis.setHasAudioData(false);
            return analysis;
        }

        analysis.setHasAudioData(true);
        analysis.setSongCount(features.size());


        analysis.setAvgEnergy(calculateAverage(features, "energy"));
        analysis.setAvgValence(calculateAverage(features, "valence"));
        analysis.setAvgDanceability(calculateAverage(features, "danceability"));
        analysis.setAvgTempo(calculateAverage(features, "tempo"));
        analysis.setAvgAcousticness(calculateAverage(features, "acousticness"));
        analysis.setAvgInstrumentalness(calculateAverage(features, "instrumentalness"));


        analysis.setEnergyDistribution(getDistribution(features, "energy"));
        analysis.setValenceDistribution(getDistribution(features, "valence"));
        analysis.setTempoDistribution(getTempoDistribution(features));


        List<String> styleTags = generateStyleTags(analysis);
        analysis.setStyleTags(styleTags);


        List<String> scenarioTags = generateScenarioTags(analysis);
        analysis.setScenarioTags(scenarioTags);


        String moodTag = generateMoodTag(analysis);
        analysis.setMoodTag(moodTag);


        double consistency = checkPlaylistConsistency(playlistId);
        analysis.setConsistencyScore(consistency);


        redisUtils.set(cacheKey, analysis, CACHE_HOURS * 3600, java.util.concurrent.TimeUnit.SECONDS);
        return analysis;
    }

    @Override
    public List<String> generateAutoTags(Long playlistId) {
        PlaylistAudioAnalysis analysis = analyzePlaylist(playlistId);
        List<String> tags = new ArrayList<>();


        tags.addAll(analysis.getScenarioTags());


        tags.addAll(analysis.getStyleTags());


        if (analysis.getMoodTag() != null) {
            tags.add(analysis.getMoodTag());
        }


        if (analysis.getAvgEnergy() != null && analysis.getAvgEnergy() > 0.7) {
            tags.add("高能");
        }
        if (analysis.getAvgDanceability() != null && analysis.getAvgDanceability() > 0.7) {
            tags.add("动感");
        }
        if (analysis.getAvgAcousticness() != null && analysis.getAvgAcousticness() > 0.6) {
            tags.add("原声");
        }
        if (analysis.getAvgInstrumentalness() != null && analysis.getAvgInstrumentalness() > 0.5) {
            tags.add("纯音乐");
        }


        return tags.stream().distinct().collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> updatePlaylistAudioTags(Long playlistId) {
        Map<String, Object> result = new HashMap<>();

        List<String> autoTags = generateAutoTags(playlistId);


        String tagsJson = String.join(",", autoTags);
        int updated = jdbcTemplate.update(
                "UPDATE playlist SET tags = ?, tags_source = 'audio_analysis', " +
                        "tags_updated = NOW() WHERE id = ?",
                tagsJson, playlistId);

        result.put("success", updated > 0);
        result.put("tags", autoTags);
        result.put("tagsCount", autoTags.size());


        redisUtils.delete(PLAYLIST_AUDIO_CACHE_PREFIX + playlistId);

        return result;
    }

    @Override
    public Map<String, Object> updatePlaylistAudioTags(Long playlistId, Long operatorId) {
        if (playlistId == null || playlistId <= 0 || operatorId == null || operatorId <= 0) {
            throw new BusinessException(400, "歌单或操作者无效");
        }
        Playlist playlist = playlistService.getById(playlistId);
        if (playlist == null || Integer.valueOf(1).equals(playlist.getDeleted())
                || !Integer.valueOf(1).equals(playlist.getStatus())) {
            throw new BusinessException("歌单不存在或不可编辑");
        }
        User operator = userMapper.selectById(operatorId);
        if (!UserAccountStatusUtil.canInteract(operator)) {
            throw new BusinessException("当前账号不可执行此操作");
        }
        if (!Objects.equals(playlist.getUserId(), operatorId)
                && !UserRole.isAdmin(operator.getRole())) {
            throw new BusinessException(403, "无权更新此歌单的音频标签");
        }
        return updatePlaylistAudioTags(playlistId);
    }

    @Override
    public Map<String, Object> getPlaylistFeatureDistribution(Long playlistId) {
        PlaylistAudioAnalysis analysis = analyzePlaylist(playlistId);

        Map<String, Object> distribution = new HashMap<>();
        distribution.put("energy", analysis.getEnergyDistribution());
        distribution.put("valence", analysis.getValenceDistribution());
        distribution.put("tempo", analysis.getTempoDistribution());
        distribution.put("avgEnergy", analysis.getAvgEnergy());
        distribution.put("avgValence", analysis.getAvgValence());
        distribution.put("avgTempo", analysis.getAvgTempo());
        distribution.put("consistency", analysis.getConsistencyScore());

        return distribution;
    }

    @Override
    public Double checkPlaylistConsistency(Long playlistId) {
        String sql = "SELECT danceability, energy, valence, tempo, s.uploader_id " +
                "FROM playlist_song ps " +
                "INNER JOIN song s ON ps.song_id = s.id " +
                "WHERE ps.playlist_id = ? AND ps.deleted = 0 AND s.status = 1 AND s.deleted = 0 " +
                "AND s.danceability IS NOT NULL";

        List<Map<String, Object>> features = filterPublicUploaderRows(jdbcTemplate.queryForList(sql, playlistId));

        if (features.size() < 2) {
            return 1.0;             
        }


        double energyStd = calculateStdDev(features, "energy");
        double valenceStd = calculateStdDev(features, "valence");
        double danceStd = calculateStdDev(features, "danceability");
        double tempoStd = calculateStdDev(features, "tempo") / 200;       


        double avgStd = (energyStd + valenceStd + danceStd + tempoStd) / 4;
        double consistency = Math.max(0, 1 - avgStd);

        return consistency;
    }

    @Override
    public int batchUpdateAllPlaylists() {
        String sql = "SELECT id FROM playlist WHERE status = 1 AND deleted = 0";
        List<Map<String, Object>> playlists = jdbcTemplate.queryForList(sql);

        int updated = 0;
        for (Map<String, Object> row : playlists) {
            Long playlistId = ((Number) row.get("id")).longValue();
            try {
                Map<String, Object> result = updatePlaylistAudioTags(playlistId);
                if ((Boolean) result.get("success")) {
                    updated++;
                }
            } catch (Exception e) {
                log.error("更新歌单音频标签失败: playlistId={}", playlistId);
            }
        }

        return updated;
    }

    @Override
    public List<Long> recommendPlaylistsByFeatures(Double valence, Double energy, Integer limit) {
        int safeLimit = safeLimit(limit);
        if (safeLimit <= 0) {
            return Collections.emptyList();
        }

        String sql = "SELECT p.id, p.user_id, s.uploader_id, s.play_count " +
                "FROM playlist p " +
                "INNER JOIN playlist_song ps ON p.id = ps.playlist_id " +
                "INNER JOIN song s ON ps.song_id = s.id " +
                "WHERE p.status = 1 AND p.deleted = 0 AND p.is_public = 1 " +
                "AND ps.deleted = 0 AND s.status = 1 AND s.deleted = 0 " +
                "AND s.valence IS NOT NULL AND s.energy IS NOT NULL " +
                "AND s.valence BETWEEN ? AND ? " +
                "AND s.energy BETWEEN ? AND ? " +
                "ORDER BY s.play_count DESC " +
                "LIMIT ?";

        double vMin = Math.max(0, valence - 0.15);
        double vMax = Math.min(1, valence + 0.15);
        double eMin = Math.max(0, energy - 0.15);
        double eMax = Math.min(1, energy + 0.15);

        List<Map<String, Object>> results = filterPublicUploaderRows(jdbcTemplate.queryForList(
                sql, vMin, vMax, eMin, eMax, playlistSongCandidateLimit(safeLimit)));

        Set<Long> creatorIds = results.stream()
                .map(row -> toLong(row.get("user_id")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedCreatorIds = UserAccountStatusUtil.filterPublicContentUserIds(
                creatorIds, ids -> userMapper.selectBatchIds(ids));

        Map<Long, PlaylistFeatureCandidate> candidates = new LinkedHashMap<>();
        for (Map<String, Object> row : results) {
            Long creatorId = toLong(row.get("user_id"));
            Long playlistId = toLong(row.get("id"));
            if (creatorId == null || playlistId == null || !allowedCreatorIds.contains(creatorId)) {
                continue;
            }
            PlaylistFeatureCandidate candidate = candidates.computeIfAbsent(
                    playlistId,
                    id -> new PlaylistFeatureCandidate()
            );
            candidate.playlistId = playlistId;
            candidate.matchCount++;
            candidate.playCountTotal += toLong(row.get("play_count")) == null ? 0L : toLong(row.get("play_count"));
        }

        return candidates.values().stream()
                .filter(candidate -> candidate.matchCount >= 3)
                .sorted((left, right) -> Double.compare(right.averagePlayCount(), left.averagePlayCount()))
                .map(candidate -> candidate.playlistId)
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    @Override
    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
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

    private int playlistSongCandidateLimit(int limit) {
        return Math.min(Math.max(candidateLimit(limit) * 20, limit * 20), 5000);
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

    private static class PlaylistFeatureCandidate {
        private Long playlistId;
        private int matchCount;
        private long playCountTotal;

        private double averagePlayCount() {
            return matchCount == 0 ? 0D : (double) playCountTotal / matchCount;
        }
    }

    private Double calculateAverage(List<Map<String, Object>> features, String key) {
        if (features.isEmpty()) return null;

        double sum = 0;
        int count = 0;
        for (Map<String, Object> feature : features) {
            Object value = feature.get(key);
            if (value instanceof BigDecimal) {
                sum += ((BigDecimal) value).doubleValue();
                count++;
            }
        }

        return count > 0 ? sum / count : null;
    }

    private double calculateStdDev(List<Map<String, Object>> features, String key) {
        Double avg = calculateAverage(features, key);
        if (avg == null) return 0;

        double sumSqDiff = 0;
        int count = 0;
        for (Map<String, Object> feature : features) {
            Object value = feature.get(key);
            if (value instanceof BigDecimal) {
                double diff = ((BigDecimal) value).doubleValue() - avg;
                sumSqDiff += diff * diff;
                count++;
            }
        }

        return count > 0 ? Math.sqrt(sumSqDiff / count) : 0;
    }

    private Map<String, Integer> getDistribution(List<Map<String, Object>> features, String key) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("低 (0-0.33)", 0);
        distribution.put("中 (0.33-0.67)", 0);
        distribution.put("高 (0.67-1.0)", 0);

        for (Map<String, Object> feature : features) {
            Object value = feature.get(key);
            if (value instanceof BigDecimal) {
                double v = ((BigDecimal) value).doubleValue();
                if (v < 0.33) {
                    distribution.put("低 (0-0.33)", distribution.get("低 (0-0.33)") + 1);
                } else if (v < 0.67) {
                    distribution.put("中 (0.33-0.67)", distribution.get("中 (0.33-0.67)") + 1);
                } else {
                    distribution.put("高 (0.67-1.0)", distribution.get("高 (0.67-1.0)") + 1);
                }
            }
        }

        return distribution;
    }

    private Map<String, Integer> getTempoDistribution(List<Map<String, Object>> features) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("慢 (0-80)", 0);
        distribution.put("中 (80-120)", 0);
        distribution.put("快 (120-160)", 0);
        distribution.put("很快 (160+)", 0);

        for (Map<String, Object> feature : features) {
            Object value = feature.get("tempo");
            if (value instanceof BigDecimal) {
                double v = ((BigDecimal) value).doubleValue();
                if (v < 80) {
                    distribution.put("慢 (0-80)", distribution.get("慢 (0-80)") + 1);
                } else if (v < 120) {
                    distribution.put("中 (80-120)", distribution.get("中 (80-120)") + 1);
                } else if (v < 160) {
                    distribution.put("快 (120-160)", distribution.get("快 (120-160)") + 1);
                } else {
                    distribution.put("很快 (160+)", distribution.get("很快 (160+)") + 1);
                }
            }
        }

        return distribution;
    }

    private List<String> generateStyleTags(PlaylistAudioAnalysis analysis) {
        List<String> tags = new ArrayList<>();

        Double energy = analysis.getAvgEnergy();
        Double danceability = analysis.getAvgDanceability();
        Double acousticness = analysis.getAvgAcousticness();
        Double instrumentalness = analysis.getAvgInstrumentalness();
        Double valence = analysis.getAvgValence();


        if (energy != null) {
            if (energy >= 0.7) tags.add("高能燃曲");
            else if (energy <= 0.3) tags.add("舒缓轻音");
        }


        if (danceability != null && danceability >= 0.7) {
            tags.add("动感舞曲");
        }


        if (acousticness != null && acousticness >= 0.6) {
            tags.add("原声民谣");
        }


        if (instrumentalness != null && instrumentalness >= 0.5) {
            tags.add("纯音乐");
        }


        if (valence != null) {
            if (valence >= 0.7) tags.add("阳光积极");
            else if (valence <= 0.3) tags.add("低沉伤感");
        }

        return tags;
    }

    private List<String> generateScenarioTags(PlaylistAudioAnalysis analysis) {
        List<String> tags = new ArrayList<>();

        Double energy = analysis.getAvgEnergy();
        Double danceability = analysis.getAvgDanceability();
        Double acousticness = analysis.getAvgAcousticness();
        Double instrumentalness = analysis.getAvgInstrumentalness();
        Double valence = analysis.getAvgValence();
        Double tempo = analysis.getAvgTempo();


        if (energy != null && danceability != null && energy >= 0.7 && danceability >= 0.6) {
            tags.add("运动健身");
        }


        if (energy != null && acousticness != null && energy <= 0.3 && acousticness >= 0.5) {
            tags.add("助眠放松");
        }


        if (instrumentalness != null && instrumentalness >= 0.5) {
            tags.add("专注学习");
        }


        if (valence != null && danceability != null && valence >= 0.6 && danceability >= 0.7) {
            tags.add("派对狂欢");
        }


        if (tempo != null && energy != null && tempo >= 120 && tempo <= 150 && energy >= 0.6) {
            tags.add("跑步伴侣");
        }

        return tags;
    }

    private String generateMoodTag(PlaylistAudioAnalysis analysis) {
        Double valence = analysis.getAvgValence();
        Double energy = analysis.getAvgEnergy();

        if (valence == null) return null;

        if (valence >= 0.7) {
            return energy != null && energy >= 0.6 ? "开心兴奋" : "轻松愉悦";
        } else if (valence >= 0.4) {
            return "平静平和";
        } else if (valence >= 0.2) {
            return energy != null && energy <= 0.4 ? "忧郁伤感" : "略带忧伤";
        } else {
            return "低落压抑";
        }
    }
}
