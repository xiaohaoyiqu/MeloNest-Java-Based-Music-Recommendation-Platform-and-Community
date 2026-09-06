package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.Song;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.DJMixService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

   
             
  
                      
   
@Slf4j
@Service
public class DJMixServiceImpl implements DJMixService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    private static final String CACHE_PREFIX = "audio:dj:";
    private static final long CANDIDATE_CACHE_MINUTES = 10L;
    private static final int MIN_DURATION_MINUTES = 15;
    private static final int MAX_DURATION_MINUTES = 180;
    private static final int MAX_MANUAL_SONGS = 29;

                                
                              
    private static final int[][] COMPATIBLE_KEYS = {
                                             
            {0, 5, 7},
                                                    
            {1, 6, 8},
                                             
            {2, 7, 9},
                                                    
            {3, 8, 10},
                                             
            {4, 9, 11},
                                              
            {5, 10, 0},
                                                    
            {6, 11, 1},
                                             
            {7, 0, 2},
                                                    
            {8, 1, 3},
                                             
            {9, 2, 4},
                                                   
            {10, 3, 5},
                                               
            {11, 4, 6}
    };

    @Override
    public List<Map<String, Object>> getMixableSongs(Long songId, Integer bpmTolerance, Integer limit) {
        log.debug("获取可混音歌曲: songId={}, bpmTolerance={}", songId, bpmTolerance);

        bpmTolerance = normalizeBpmTolerance(bpmTolerance);
        if (limit == null || limit <= 0) {
            limit = 20;
        }

        Song referenceSong = songMapper.selectById(songId);
        if (!canExposeSong(referenceSong)) {
            return Collections.emptyList();
        }

        int actualLimit = safeLimit(limit, 20, 100);
        Double refBpm = referenceSong.getTempo() != null ? referenceSong.getTempo().doubleValue() : null;
        List<Song> songs = getMixableSongCandidates(referenceSong, bpmTolerance, actualLimit);

        return songs.stream()
                .limit(actualLimit)
                .map(s -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", s.getId());
                    info.put("name", s.getName());
                    info.put("artistNames", s.getArtistNames());
                    info.put("albumId", s.getAlbumId());
                    info.put("albumName", s.getAlbumName());
                    info.put("cover", s.getCover());
                    info.put("duration", s.getDuration());
                    info.put("bpm", s.getTempo());
                    info.put("key", s.getAudioKey());
                    info.put("mode", s.getMode());
                    info.put("energy", s.getEnergy());
                    info.put("danceability", s.getDanceability());
                    info.put("bpmDiff", Math.abs(s.getTempo() != null ? s.getTempo().doubleValue() - (refBpm != null ? refBpm : 0.0) : 0.0));
                    return info;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> generateMixPlaylist(Long startSongId, Integer durationMinutes, Long userId) {
        return generateMixPlaylist(startSongId, durationMinutes, userId, null);
    }

    @Override
    public Map<String, Object> generateMixPlaylist(Long startSongId, Integer durationMinutes,
                                                   Long userId, List<Long> selectedSongIds) {
        log.debug("生成混音歌单: startSongId={}, duration={}", startSongId, durationMinutes);

        int safeDuration = validateDuration(durationMinutes);
        List<Long> manualIds = normalizeManualSongIds(startSongId, selectedSongIds);
        if (!manualIds.isEmpty()) {
            return generateManualMixPlaylist(startSongId, safeDuration, manualIds);
        }

                   
        int targetCount = Math.min((safeDuration / 4) + 1, 46);

        List<Map<String, Object>> mixPlaylist = new ArrayList<>();
        Set<Long> usedIds = new HashSet<>();
        usedIds.add(startSongId);

        Song currentSong = songMapper.selectById(startSongId);
        if (!canExposeSong(currentSong) || !hasPlayableAudio(currentSong)) {
            return Collections.emptyMap();
        }

                 
        mixPlaylist.add(createMixEntry(currentSong, 0, 0.0));

                   
        for (int i = 1; i < targetCount; i++) {
            List<Song> availableSongs = getMixableSongCandidates(currentSong, 10, 50).stream()
                    .filter(song -> !usedIds.contains(song.getId()))
                    .collect(Collectors.toList());

            if (availableSongs.isEmpty()) {
                break;
            }

                     
            Song selectedSong = availableSongs.get(ThreadLocalRandom.current().nextInt(availableSongs.size()));
            mixPlaylist.add(createMixEntry(selectedSong, i, calculateBpmDiff(currentSong, selectedSong)));
            usedIds.add(selectedSong.getId());
            currentSong = selectedSong;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("songs", mixPlaylist);
        result.put("totalSongs", mixPlaylist.size());
        result.put("estimatedDuration", mixPlaylist.stream()
                .mapToInt(songData -> {
                    Object duration = songData.get("duration");
                    return duration instanceof Number ? ((Number) duration).intValue() : 240;
                }).sum() / 60);
        result.put("type", "dj_mix");
        boolean formed = mixPlaylist.size() > 1;
        String status = formed ? "ready" : resolveIncompleteMixStatus(currentSong);
        result.put("formed", formed);
        result.put("status", status);
        result.put("requestedDuration", safeDuration);
        result.put("targetSongs", targetCount);
        result.put("selectionMode", "automatic");
        result.put("message", resolveMixStatusMessage(status));

        log.info("event=dj_mix_generated songId={} requestedDuration={} targetSongs={} actualSongs={} status={}",
                startSongId, safeDuration, targetCount, mixPlaylist.size(), status);

        return result;
    }

    private Map<String, Object> generateManualMixPlaylist(Long startSongId, int durationMinutes,
                                                          List<Long> selectedSongIds) {
        Song startSong = songMapper.selectById(startSongId);
        if (!canExposeSong(startSong) || !hasRequiredMixFeatures(startSong) || !hasPlayableAudio(startSong)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "起始歌曲缺少可用音源或混音资料");
        }

        List<Song> selectedSongs = songMapper.selectBatchIds(selectedSongIds);
        List<Song> publicSelectedSongs = filterPublicSongs(selectedSongs);
        Map<Long, Song> songsById = publicSelectedSongs.stream()
                .collect(Collectors.toMap(Song::getId, song -> song, (left, right) -> left));

        List<Song> orderedSongs = new ArrayList<>();
        orderedSongs.add(startSong);
        for (Long selectedId : selectedSongIds) {
            Song selected = songsById.get(selectedId);
            if (!hasRequiredMixFeatures(selected)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "手选队列中有歌曲已下架、不可播放或缺少混音资料");
            }
            orderedSongs.add(selected);
        }

        List<Map<String, Object>> mixPlaylist = new ArrayList<>();
        Song previous = null;
        for (int index = 0; index < orderedSongs.size(); index++) {
            Song current = orderedSongs.get(index);
            if (previous != null && !areSongsMixable(previous, current, 10)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "手选顺序中有相邻歌曲的 BPM 或调式接不上，请调整顺序");
            }
            double transition = previous == null ? 0.0 : calculateBpmDiff(previous, current);
            mixPlaylist.add(createMixEntry(current, index, transition));
            previous = current;
        }

        Map<String, Object> result = buildMixResult(
                mixPlaylist, durationMinutes, orderedSongs.size(), "manual", "ready");
        log.info("event=dj_manual_mix_generated songId={} requestedDuration={} actualSongs={}",
                startSongId, durationMinutes, mixPlaylist.size());
        return result;
    }

    private Map<String, Object> buildMixResult(List<Map<String, Object>> songs, int durationMinutes,
                                               int targetSongs, String selectionMode, String status) {
        Map<String, Object> result = new HashMap<>();
        result.put("songs", songs);
        result.put("totalSongs", songs.size());
        result.put("estimatedDuration", songs.stream()
                .mapToInt(songData -> songData.get("duration") instanceof Number
                        ? ((Number) songData.get("duration")).intValue() : 240)
                .sum() / 60);
        result.put("type", "dj_mix");
        result.put("formed", songs.size() > 1);
        result.put("status", status);
        result.put("requestedDuration", durationMinutes);
        result.put("targetSongs", targetSongs);
        result.put("selectionMode", selectionMode);
        result.put("message", resolveMixStatusMessage(status));
        return result;
    }

    private int validateDuration(Integer durationMinutes) {
        int duration = durationMinutes == null ? 60 : durationMinutes;
        if (duration < MIN_DURATION_MINUTES || duration > MAX_DURATION_MINUTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "混音时长应在15至180分钟之间");
        }
        return duration;
    }

    private List<Long> normalizeManualSongIds(Long startSongId, List<Long> selectedSongIds) {
        if (startSongId == null || startSongId <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "起始歌曲ID不合法");
        }
        if (selectedSongIds == null || selectedSongIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (selectedSongIds.size() > MAX_MANUAL_SONGS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "一次最多手选29首歌曲");
        }

        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long selectedId : selectedSongIds) {
            if (selectedId == null || selectedId <= 0 || selectedId.equals(startSongId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "手选歌曲ID不合法或包含当前歌曲");
            }
            if (!uniqueIds.add(selectedId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "手选队列不能包含重复歌曲");
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    @Override
    public Map<String, Object> checkMixable(Long songId1, Long songId2) {
        log.debug("检查可混音性: songId1={}, songId2={}", songId1, songId2);

        Song song1 = songMapper.selectById(songId1);
        Song song2 = songMapper.selectById(songId2);

        if (!canExposeSong(song1) || !canExposeSong(song2)) {
            Map<String, Object> result = new HashMap<>();
            result.put("mixable", false);
            result.put("reason", "歌曲不存在或不可用");
            return result;
        }

        Double bpm1 = song1.getTempo() != null ? song1.getTempo().doubleValue() : null;
        Double bpm2 = song2.getTempo() != null ? song2.getTempo().doubleValue() : null;
        Integer key1 = song1.getAudioKey();
        Integer key2 = song2.getAudioKey();
        Integer mode1 = song1.getMode();
        Integer mode2 = song2.getMode();

        Map<String, Object> result = new HashMap<>();

                   
        boolean bpmCompatible = false;
        if (bpm1 != null && bpm2 != null) {
            double bpmDiff = Math.abs(bpm1 - bpm2);
            bpmCompatible = bpmDiff <= 10 || isBpmMultiple(bpm1, bpm2);
            result.put("bpmDiff", bpmDiff);
            result.put("bpmCompatible", bpmCompatible);
        }

                  
        boolean keyCompatible = false;
        if (key1 != null && key2 != null) {
            List<Integer> compatibleKeys = getCompatibleKeyList(key1, mode1 != null ? mode1 : 1);
            keyCompatible = compatibleKeys.contains(key2);
            result.put("keyCompatible", keyCompatible);
        }

               
        boolean mixable = (bpm1 == null || bpm2 == null || bpmCompatible) && keyCompatible;
        result.put("mixable", mixable);

        if (!mixable) {
            List<String> reasons = new ArrayList<>();
            if (!bpmCompatible) {
                reasons.add("BPM差异过大");
            }
            if (!keyCompatible) {
                reasons.add("调式不兼容");
            }
            result.put("reasons", reasons);
        }

        return result;
    }

    @Override
    public Map<String, Object> getSongMixInfo(Long songId) {
        Song song = songMapper.selectById(songId);

        if (!canExposeSong(song)) {
            return Collections.emptyMap();
        }

        Map<String, Object> info = new HashMap<>();
        info.put("songId", song.getId());
        info.put("songName", song.getName());
        info.put("bpm", song.getTempo());
        info.put("key", song.getAudioKey());
        info.put("mode", song.getMode());
        info.put("modeName", song.getMode() != null ? (song.getMode() == 1 ? "大调" : "小调") : "未知");
        info.put("energy", song.getEnergy());
        info.put("danceability", song.getDanceability());
        info.put("featureReady", hasRequiredMixFeatures(song));
        info.put("featureStatus", hasRequiredMixFeatures(song) ? "ready" : "missing_audio_features");

               
        if (song.getAudioKey() != null) {
            info.put("compatibleKeys", getCompatibleKeys(song.getAudioKey(), song.getMode()));
        }

        return info;
    }

    @Override
    public List<Map<String, Object>> getCompatibleKeys(Integer key, Integer mode) {
        if (key == null) {
            key = 0;
        }
        if (mode == null) {
            mode = 1;
        }

        key = Math.max(0, Math.min(11, key));

        List<Map<String, Object>> result = new ArrayList<>();
        int[] compatible = COMPATIBLE_KEYS[key];

        String[] keyNames = {"C", "C#/Db", "D", "D#/Eb", "E", "F", "F#/Gb", "G", "Ab/G#", "A", "Bb/A#", "B"};

        for (int k : compatible) {
            Map<String, Object> keyInfo = new HashMap<>();
            keyInfo.put("key", k);
            keyInfo.put("keyName", keyNames[k]);
            keyInfo.put("mode", mode);
            keyInfo.put("modeName", mode == 1 ? "大调" : "小调");
            result.add(keyInfo);
        }

        return result;
    }

    @Override
    public Map<String, Object> calculateBpmConversion(Double sourceBpm, Double targetBpm) {
        Map<String, Object> result = new HashMap<>();

        if (sourceBpm == null || targetBpm == null || sourceBpm == 0) {
            result.put("error", "无效的BPM值");
            return result;
        }

        double ratio = targetBpm / sourceBpm;
        result.put("sourceBpm", sourceBpm);
        result.put("targetBpm", targetBpm);
        result.put("ratio", ratio);
        result.put("percentage", (ratio - 1) * 100);

                   
        if (Math.abs(ratio - 1.0) <= 0.05) {
            result.put("recommendation", "无需调整，BPM已匹配");
        } else if (ratio >= 0.95 && ratio <= 1.05) {
            result.put("recommendation", "微小调整即可");
        } else if (ratio >= 0.8 && ratio <= 1.25) {
            result.put("recommendation", "可以调整，但可能影响音质");
        } else {
            result.put("recommendation", "不建议调整，差异过大");
        }

        return result;
    }

                                                       

    private List<Integer> getCompatibleKeyList(Integer key, Integer mode) {
        if (key == null) {
            key = 0;
        }
        key = Math.max(0, Math.min(11, key));

        int[] compatible = COMPATIBLE_KEYS[key];
        List<Integer> result = new ArrayList<>();
        for (int k : compatible) {
            result.add(k);
        }
        return result;
    }

    private boolean isBpmMultiple(Double bpm1, Double bpm2) {
        if (bpm1 == null || bpm2 == null || bpm1 == 0 || bpm2 == 0) {
            return false;
        }
        double ratio = bpm1 / bpm2;
                        
        return Math.abs(ratio - 2.0) < 0.05 || Math.abs(ratio - 0.5) < 0.05;
    }

    private Map<String, Object> createMixEntry(Song song, int index, double bpmDiff) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", song.getId());
        entry.put("name", song.getName());
        entry.put("artistNames", song.getArtistNames());
        entry.put("cover", song.getCover());
        entry.put("bpm", song.getTempo());
        entry.put("key", song.getAudioKey());
        entry.put("duration", song.getDuration());
        entry.put("position", index);
        entry.put("bpmTransition", bpmDiff);
        return entry;
    }

    private List<Song> getMixableSongCandidates(Song referenceSong, Integer bpmTolerance, Integer limit) {
        if (!canExposeSong(referenceSong)) {
            return Collections.emptyList();
        }
        Double refBpm = referenceSong.getTempo() != null ? referenceSong.getTempo().doubleValue() : null;
        if (!hasRequiredMixFeatures(referenceSong) || refBpm == null) {
            return Collections.emptyList();
        }

        int actualTolerance = normalizeBpmTolerance(bpmTolerance);
        int actualLimit = safeLimit(limit, 20, 100);
        String cacheKey = CACHE_PREFIX + musicIntelligenceCacheService.candidateVersionSegment()
                + "candidates:" + referenceSong.getId() + ":" + actualTolerance + ":"
                + actualLimit + ":" + refBpm + ":" + referenceSong.getAudioKey() + ":" + referenceSong.getMode();
        return cacheSongList(cacheKey,
                () -> loadMixableSongCandidates(referenceSong, refBpm, actualTolerance, actualLimit),
                CANDIDATE_CACHE_MINUTES);
    }

    private List<Song> loadMixableSongCandidates(Song referenceSong, Double refBpm,
                                                 int actualTolerance, int actualLimit) {
        List<Integer> compatibleKeys = getCompatibleKeyList(
                referenceSong.getAudioKey() != null ? referenceSong.getAudioKey() : 0,
                referenceSong.getMode() != null ? referenceSong.getMode() : 1);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .ne(Song::getId, referenceSong.getId())
                .between(Song::getTempo, refBpm - actualTolerance, refBpm + actualTolerance);

        if (referenceSong.getAudioKey() != null) {
            wrapper.in(Song::getAudioKey, compatibleKeys);
        }

        wrapper.last("ORDER BY ABS(tempo - " + refBpm + ") LIMIT " + candidateLimit(actualLimit));
        return filterPublicSongs(songMapper.selectList(wrapper)).stream()
                .limit(actualLimit)
                .collect(Collectors.toList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Song> cacheSongList(String key, Supplier<List<Song>> supplier, long ttlMinutes) {
        try {
            return (List<Song>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("event=dj_mix_candidate_cache_read_failed fallback=realtime_calculation errorType={}",
                    e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    private double calculateBpmDiff(Song referenceSong, Song selectedSong) {
        Double referenceBpm = referenceSong.getTempo() != null ? referenceSong.getTempo().doubleValue() : 0.0;
        Double selectedBpm = selectedSong.getTempo() != null ? selectedSong.getTempo().doubleValue() : 0.0;
        return Math.abs(selectedBpm - referenceBpm);
    }

    private int safeLimit(Integer limit, int defaultLimit, int maxLimit) {
        int value = limit != null && limit > 0 ? limit : defaultLimit;
        return Math.min(value, maxLimit);
    }

    private int normalizeBpmTolerance(Integer bpmTolerance) {
        int value = bpmTolerance != null && bpmTolerance > 0 ? bpmTolerance : 10;
        return Math.min(value, 40);
    }

    private int candidateLimit(int limit) {
        return Math.min(Math.max(limit * 3, limit), 300);
    }

    private boolean hasRequiredMixFeatures(Song song) {
        return song != null && song.getTempo() != null && song.getAudioKey() != null && song.getMode() != null;
    }

    private boolean areSongsMixable(Song source, Song target, int bpmTolerance) {
        if (!hasRequiredMixFeatures(source) || !hasRequiredMixFeatures(target)) {
            return false;
        }
        double sourceBpm = source.getTempo().doubleValue();
        double targetBpm = target.getTempo().doubleValue();
        boolean bpmCompatible = Math.abs(sourceBpm - targetBpm) <= bpmTolerance
                || isBpmMultiple(sourceBpm, targetBpm);
        return bpmCompatible && getCompatibleKeyList(source.getAudioKey(), source.getMode())
                .contains(target.getAudioKey());
    }

    private boolean hasPlayableAudio(Song song) {
        return song != null && (hasText(song.getUrlStandard())
                || hasText(song.getUrlHigh()) || hasText(song.getUrlLossless()));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String resolveIncompleteMixStatus(Song song) {
        return hasRequiredMixFeatures(song) ? "no_compatible_tracks" : "missing_audio_features";
    }

    private String resolveMixStatusMessage(String status) {
        if ("ready".equals(status)) {
            return "DJ mix queue generated";
        }
        if ("missing_audio_features".equals(status)) {
            return "Source song is missing BPM or key features";
        }
        return "No compatible tracks are currently available";
    }

    private List<Song> filterPublicSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> uploaderIds = songs.stream()
                .map(Song::getUploaderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> allowedUploaderIds = UserAccountStatusUtil.filterPublicContentUserIds(
                uploaderIds,
                ids -> userMapper.selectBatchIds(ids)
        );

        return songs.stream()
                .filter(song -> isNormalSong(song) && hasPlayableAudio(song)
                        && (song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId())))
                .collect(Collectors.toList());
    }

    private boolean canExposeSong(Song song) {
        return isNormalSong(song)
                && (song.getUploaderId() == null
                || UserAccountStatusUtil.canExposePublicContent(song.getUploaderId(), userMapper::selectById));
    }

    private boolean isNormalSong(Song song) {
        return song != null
                && CommonConstants.STATUS_NORMAL.equals(song.getStatus())
                && CommonConstants.NOT_DELETED.equals(song.getDeleted());
    }
}
