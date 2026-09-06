package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.CacheHelper;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.RedisUtils;
import com.haoran.music.common.util.SecurityCheckUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.service.SmartPlaylistService;
import com.haoran.music.service.SongService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;






@Slf4j
@Service
public class SmartPlaylistServiceImpl implements SmartPlaylistService {

    @Resource
    private SongMapper songMapper;

    @Resource
    private PlaylistMapper playlistMapper;

    @Resource
    private PlaylistSongMapper playlistSongMapper;

    @Resource
    private SongService songService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtils redisUtils;

    @Resource
    private MusicIntelligenceCacheService musicIntelligenceCacheService;

    private static final String CACHE_PREFIX = "audio:smart-playlist:";
    private static final long CANDIDATE_CACHE_MINUTES = 10L;
    private static final int MAX_SAVED_SONGS = 60;
    private static final int MAX_PLAYLIST_NAME_LENGTH = 50;
    private static final int MAX_PLAYLIST_DESCRIPTION_LENGTH = 200;
    private static final int MAX_SAVED_DURATION_SECONDS = 240 * 60;


    private static final Map<String, Map<String, double[]>> ACTIVITY_PROFILES = new HashMap<>();

    static {

        Map<String, double[]> running = new HashMap<>();
        running.put("energy", new double[]{0.7, 1.0});
        running.put("danceability", new double[]{0.6, 1.0});
        running.put("tempo", new double[]{120, 180});
        ACTIVITY_PROFILES.put("running", running);

        Map<String, double[]> workingOut = new HashMap<>();
        workingOut.put("energy", new double[]{0.6, 1.0});
        workingOut.put("danceability", new double[]{0.5, 1.0});
        workingOut.put("tempo", new double[]{100, 160});
        ACTIVITY_PROFILES.put("working_out", workingOut);


        Map<String, double[]> studying = new HashMap<>();
        studying.put("energy", new double[]{0.1, 0.5});
        studying.put("valence", new double[]{0.3, 0.7});
        studying.put("tempo", new double[]{60, 100});
        ACTIVITY_PROFILES.put("studying", studying);


        Map<String, double[]> sleeping = new HashMap<>();
        sleeping.put("energy", new double[]{0.0, 0.3});
        sleeping.put("danceability", new double[]{0.0, 0.4});
        sleeping.put("tempo", new double[]{40, 80});
        ACTIVITY_PROFILES.put("sleeping", sleeping);


        Map<String, double[]> party = new HashMap<>();
        party.put("energy", new double[]{0.7, 1.0});
        party.put("valence", new double[]{0.6, 1.0});
        party.put("danceability", new double[]{0.7, 1.0});
        ACTIVITY_PROFILES.put("party", party);


        Map<String, double[]> commuting = new HashMap<>();
        commuting.put("energy", new double[]{0.4, 0.8});
        commuting.put("valence", new double[]{0.4, 0.9});
        commuting.put("tempo", new double[]{80, 140});
        ACTIVITY_PROFILES.put("commuting", commuting);
    }

    @Override
    public Map<String, Object> generatePlaylistByPrompt(Long userId, String prompt, Integer durationMinutes) {
        log.debug("根据提示生成播放列表: userId={}, prompt={}", userId, prompt);


        String activity = inferActivityFromPrompt(prompt);

        if (activity != null) {
            return generatePlaylistByActivity(userId, activity, durationMinutes);
        }


        return getDefaultPlaylist(userId, durationMinutes);
    }

    @Override
    public Map<String, Object> generatePlaylistByActivity(Long userId, String activity, Integer durationMinutes) {
        log.debug("根据活动生成播放列表: userId={}, activity={}", userId, activity);

        Map<String, double[]> profile = ACTIVITY_PROFILES.get(activity);
        if (profile == null) {
            return getDefaultPlaylist(userId, durationMinutes);
        }

        int targetCount = calculateSongCount(durationMinutes);


        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .isNotNull(Song::getEnergy);


        applyAudioFilters(wrapper, profile);

        List<Song> songs = selectRandomPublicCandidates("activity:" + activity + ":" + targetCount,
                wrapper, targetCount);

        return buildPlaylistResult(songs, activity, durationMinutes, describeProfile(profile));
    }

    @Override
    public Map<String, Object> generatePlaylistBySeeds(Long userId, List<Long> seedSongIds, Integer durationMinutes) {
        log.debug("根据种子歌曲生成播放列表: userId={}, seeds={}", userId, seedSongIds);

        if (seedSongIds == null || seedSongIds.isEmpty()) {
            return getDefaultPlaylist(userId, durationMinutes);
        }


        List<Song> seedSongs = filterPublicSongs(songMapper.selectBatchIds(seedSongIds));
        if (seedSongs.isEmpty()) {
            return getDefaultPlaylist(userId, durationMinutes);
        }

        Map<String, Double> avgFeatures = calculateAverageFeatures(seedSongs);
        int targetCount = calculateSongCount(durationMinutes);
        List<Long> publicSeedSongIds = seedSongs.stream()
                .map(Song::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());


        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .isNotNull(Song::getEnergy)
                .notIn(Song::getId, publicSeedSongIds);


        applyFeatureRange(wrapper, avgFeatures, 0.2);

        List<Song> songs = selectRandomPublicCandidates(wrapper, targetCount);

        return buildPlaylistResult(songs, "seeds", durationMinutes,
                Collections.singletonList("接近种子歌曲的平均音频特征"));
    }

    @Override
    public Map<String, Object> generateGradualPlaylist(Long userId, Double startEnergy, Double endEnergy, Integer songCount) {
        log.debug("生成渐进式播放列表: startEnergy={}, endEnergy={}, count={}", startEnergy, endEnergy, songCount);

        if (songCount == null || songCount <= 0) {
            songCount = 20;
        }
        songCount = Math.min(songCount, 100);

        List<Song> result = new ArrayList<>();
        double energyStep = (endEnergy - startEnergy) / songCount;

        for (int i = 0; i < songCount; i++) {
            double targetEnergy = startEnergy + (energyStep * i);
            targetEnergy = Math.max(0, Math.min(1, targetEnergy));


            LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Song::getStatus, 1)
                    .eq(Song::getDeleted, 0)
                    .isNotNull(Song::getEnergy);


            wrapper.between(Song::getEnergy, targetEnergy - 0.1, targetEnergy + 0.1);


            if (!result.isEmpty()) {
                List<Long> excludeIds = result.stream().map(Song::getId).collect(Collectors.toList());
                wrapper.notIn(Song::getId, excludeIds);
            }

            wrapper.last("ORDER BY ABS(energy - " + targetEnergy + ") LIMIT " + candidateLimit(1));

            List<Song> songs = filterPublicSongs(songMapper.selectList(wrapper));
            if (!songs.isEmpty()) {
                result.add(songs.get(0));
            }
        }

        Map<String, Object> playlistData = new HashMap<>();
        playlistData.put("songs", convertToSimpleVOList(result));
        playlistData.put("totalDuration", result.stream().mapToInt(s -> s.getDuration() != null ? s.getDuration() : 0).sum());
        playlistData.put("songCount", result.size());
        playlistData.put("type", "gradual");
        playlistData.put("startEnergy", startEnergy);
        playlistData.put("endEnergy", endEnergy);
        playlistData.put("matchedFeatures", Arrays.asList(
                String.format(Locale.ROOT, "能量从 %.2f 递进到 %.2f", startEnergy, endEnergy),
                "按能量范围逐步去重选曲"
        ));

        return playlistData;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveGeneratedPlaylist(Long userId, String name, String description, List<Long> songIds) {
        log.debug("event=smart_playlist_saved userId={}", userId);

        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty() || safeName.length() > MAX_PLAYLIST_NAME_LENGTH) {
            throw new IllegalArgumentException("歌单名称长度必须为1到50个字符");
        }
        SecurityCheckUtil.CheckResult nameCheck = SecurityCheckUtil.checkName(safeName, "歌单名称");
        if (!nameCheck.isSafe()) {
            throw new IllegalArgumentException(nameCheck.getMessage());
        }
        String safeDescription = description == null ? "" : description.trim();
        if (safeDescription.length() > MAX_PLAYLIST_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("歌单描述不能超过200个字符");
        }
        SecurityCheckUtil.CheckResult descriptionCheck = SecurityCheckUtil.checkDescription(safeDescription);
        if (!descriptionCheck.isSafe()) {
            throw new IllegalArgumentException(descriptionCheck.getMessage());
        }
        List<Long> orderedIds = validateSavedSongIds(songIds);
        UserAccountStatusUtil.requireCanInteract(userId, userMapper::selectById, "保存智能歌单");
        List<Song> publicSongs = loadPublicSongsInOrder(orderedIds);
        long totalDurationSeconds = publicSongs.stream()
                .map(Song::getDuration)
                .filter(Objects::nonNull)
                .filter(duration -> duration > 0)
                .mapToLong(Integer::longValue)
                .sum();
        if (totalDurationSeconds > MAX_SAVED_DURATION_SECONDS) {
            throw new IllegalArgumentException("智能歌单总时长不能超过240分钟");
        }


        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        playlist.setName(nameCheck.getCleanedValue());
        playlist.setDescription(safeDescription.isEmpty()
                ? "智能生成" : SecurityCheckUtil.escapeHtml(descriptionCheck.getCleanedValue()));
        playlist.setIsPublic(0);
        playlist.setSongCount(0L);
        playlist.setStatus(1);
        playlist.setDeleted(0);
        playlist.setCreateTime(LocalDateTime.now());
        playlist.setUpdateTime(LocalDateTime.now());

        if (playlistMapper.insert(playlist) != 1 || playlist.getId() == null) {
            throw new IllegalStateException("创建智能歌单失败");
        }

        LocalDateTime baseTime = LocalDateTime.now();
        List<PlaylistSong> relations = new ArrayList<>(orderedIds.size());
        for (int index = 0; index < orderedIds.size(); index++) {
            PlaylistSong relation = new PlaylistSong();
            relation.setPlaylistId(playlist.getId());
            relation.setSongId(orderedIds.get(index));
            relation.setSortOrder(index);
            relation.setAddTime(baseTime.plusSeconds(index));
            relations.add(relation);
        }
        if (playlistSongMapper.insertBatch(relations) != relations.size()) {
            throw new IllegalStateException("保存智能歌单歌曲失败");
        }
        playlist.setSongCount((long) orderedIds.size());
        if (playlistMapper.updateById(playlist) != 1) {
            throw new IllegalStateException("更新智能歌单数量失败");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("playlistId", playlist.getId());
        result.put("name", playlist.getName());
        result.put("songCount", playlist.getSongCount());
        result.put("totalDurationSeconds", totalDurationSeconds);

        return result;
    }

    @Override
    public List<String> getPlaylistNameSuggestions(String activity) {
        List<String> suggestions = new ArrayList<>();

        switch (activity) {
            case "running":
                suggestions.add("跑步专属");
                suggestions.add("奔跑节奏");
                suggestions.add("晨跑动力");
                break;
            case "studying":
                suggestions.add("专注学习");
                suggestions.add("学习背景音乐");
                suggestions.add("深度专注");
                break;
            case "sleeping":
                suggestions.add("助眠音乐");
                suggestions.add("晚安时光");
                suggestions.add("深度睡眠");
                break;
            case "party":
                suggestions.add("派对模式");
                suggestions.add("狂欢时刻");
                suggestions.add("动感节拍");
                break;
            case "working_out":
                suggestions.add("健身燃脂");
                suggestions.add("力量训练");
                suggestions.add("有氧运动");
                break;
            case "commuting":
                suggestions.add("通勤路上");
                suggestions.add("城市漫游");
                suggestions.add("旅行时光");
                break;
            default:
                suggestions.add("我的精选");
                suggestions.add("智能推荐");
                break;
        }

        return suggestions;
    }



    private String inferActivityFromPrompt(String prompt) {
        if (prompt == null) return null;

        String lower = prompt.toLowerCase();

        if (lower.contains("跑") || lower.contains("运动") || lower.contains("run")) {
            return "running";
        } else if (lower.contains("学习") || lower.contains("学习") || lower.contains("study")) {
            return "studying";
        } else if (lower.contains("睡") || lower.contains("sleep")) {
            return "sleeping";
        } else if (lower.contains("派对") || lower.contains("party")) {
            return "party";
        } else if (lower.contains("健身") || lower.contains("workout")) {
            return "working_out";
        } else if (lower.contains("通勤") || lower.contains("commute")) {
            return "commuting";
        }

        return null;
    }

    private int calculateSongCount(Integer durationMinutes) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return 20;
        }

        return (durationMinutes / 4) + 1;
    }

    private void applyAudioFilters(LambdaQueryWrapper<Song> wrapper, Map<String, double[]> profile) {
        for (Map.Entry<String, double[]> entry : profile.entrySet()) {
            String field = entry.getKey();
            double[] range = entry.getValue();

            switch (field) {
                case "energy":
                    wrapper.between(Song::getEnergy, range[0], range[1]);
                    break;
                case "danceability":
                    wrapper.between(Song::getDanceability, range[0], range[1]);
                    break;
                case "valence":
                    wrapper.between(Song::getValence, range[0], range[1]);
                    break;
                case "tempo":
                    wrapper.between(Song::getTempo, range[0], range[1]);
                    break;
            }
        }
    }

    private Map<String, Double> calculateAverageFeatures(List<Song> songs) {
        Map<String, Double> features = new HashMap<>();
        features.put("energy", songs.stream().map(Song::getEnergy).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0.5));
        features.put("danceability", songs.stream().map(Song::getDanceability).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0.5));
        features.put("valence", songs.stream().map(Song::getValence).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0.5));
        features.put("tempo", songs.stream().map(Song::getTempo).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(120.0));

        return features;
    }

    private void applyFeatureRange(LambdaQueryWrapper<Song> wrapper, Map<String, Double> features, double tolerance) {
        Double energy = features.get("energy");
        if (energy != null) {
            wrapper.between(Song::getEnergy, energy * (1 - tolerance), energy * (1 + tolerance));
        }

        Double danceability = features.get("danceability");
        if (danceability != null) {
            wrapper.between(Song::getDanceability, danceability * (1 - tolerance), danceability * (1 + tolerance));
        }

        Double valence = features.get("valence");
        if (valence != null) {
            wrapper.between(Song::getValence, valence * (1 - tolerance), valence * (1 + tolerance));
        }
    }

    private Map<String, Object> buildPlaylistResult(List<Song> songs, String type, Integer durationMinutes) {
        return buildPlaylistResult(songs, type, durationMinutes,
                Collections.singletonList("热门、公开且可播放歌曲"));
    }

    private Map<String, Object> buildPlaylistResult(List<Song> songs,
                                                    String type,
                                                    Integer durationMinutes,
                                                    List<String> matchedFeatures) {
        Map<String, Object> result = new HashMap<>();
        result.put("songs", convertToSimpleVOList(songs));
        result.put("totalDuration", songs.stream().mapToInt(s -> s.getDuration() != null ? s.getDuration() : 0).sum());
        result.put("songCount", songs.size());
        result.put("type", type);
        result.put("durationMinutes", durationMinutes);
        result.put("matchedFeatures", matchedFeatures);

        return result;
    }

    private List<Long> validateSavedSongIds(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            throw new IllegalArgumentException("歌曲列表不能为空");
        }
        if (songIds.size() > MAX_SAVED_SONGS) {
            throw new IllegalArgumentException("智能歌单最多保存60首歌曲");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long songId : songIds) {
            if (songId == null || songId <= 0) {
                throw new IllegalArgumentException("歌曲ID不合法");
            }
            if (!ids.add(songId)) {
                throw new IllegalArgumentException("歌曲列表不能包含重复ID");
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<String, Object> getDefaultPlaylist(Long userId, Integer durationMinutes) {
        int targetCount = calculateSongCount(durationMinutes);

        LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Song::getStatus, 1)
                .eq(Song::getDeleted, 0)
                .eq(Song::getIsHot, 1)
                .orderByDesc(Song::getPlayCount)
                .last("LIMIT " + candidateLimit(targetCount));

        List<Song> songs = shuffleAndLimit(
                cacheSongList(candidateCacheKey("default:" + targetCount),
                        () -> loadPublicCandidates(wrapper, targetCount), CANDIDATE_CACHE_MINUTES),
                targetCount);

        return buildPlaylistResult(songs, "default", durationMinutes,
                Arrays.asList("热门歌曲", "公开且可播放资源"));
    }

    private List<String> describeProfile(Map<String, double[]> profile) {
        List<String> features = new ArrayList<>();
        for (Map.Entry<String, double[]> entry : profile.entrySet()) {
            double[] range = entry.getValue();
            String label;
            switch (entry.getKey()) {
                case "energy":
                    label = "能量";
                    break;
                case "danceability":
                    label = "舞动性";
                    break;
                case "valence":
                    label = "情绪积极度";
                    break;
                case "tempo":
                    label = "节奏";
                    break;
                default:
                    label = entry.getKey();
                    break;
            }
            features.add(String.format(Locale.ROOT, "%s %.2f-%.2f", label, range[0], range[1]));
        }
        return features;
    }

    private List<Map<String, Object>> convertToSimpleVOList(List<Song> songs) {
        return songs.stream().map(song -> {
            Map<String, Object> vo = new HashMap<>();
            vo.put("id", song.getId());
            vo.put("name", song.getName());
            vo.put("artistNames", song.getArtistNames());
            vo.put("albumName", song.getAlbumName());
            vo.put("duration", song.getDuration());
            vo.put("cover", song.getCover());
            return vo;
        }).collect(Collectors.toList());
    }

    private int candidateLimit(int limit) {
        return Math.min(Math.max(limit * 3, limit), 300);
    }

    private List<Song> selectRandomPublicCandidates(LambdaQueryWrapper<Song> wrapper, int targetCount) {
        return shuffleAndLimit(loadPublicCandidates(wrapper, targetCount), targetCount);
    }

    private List<Song> selectRandomPublicCandidates(String cacheScope, LambdaQueryWrapper<Song> wrapper, int targetCount) {
        return shuffleAndLimit(
                cacheSongList(candidateCacheKey(cacheScope),
                        () -> loadPublicCandidates(wrapper, targetCount), CANDIDATE_CACHE_MINUTES),
                targetCount);
    }

    private String candidateCacheKey(String scope) {
        return CACHE_PREFIX + musicIntelligenceCacheService.candidateVersionSegment() + scope;
    }

    private List<Song> loadPublicCandidates(LambdaQueryWrapper<Song> wrapper, int targetCount) {
        wrapper.orderByDesc(Song::getPlayCount)
                .last("LIMIT " + candidateLimit(targetCount));
        return filterPublicSongs(songMapper.selectList(wrapper));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Song> cacheSongList(String key, Supplier<List<Song>> supplier, long ttlMinutes) {
        try {
            return (List<Song>) CacheHelper.getOrLoad(
                    redisUtils, key, (Supplier) supplier, ttlMinutes, TimeUnit.MINUTES, List.class);
        } catch (Exception e) {
            log.warn("智能歌单候选缓存读取失败，回退实时计算: key={}, error={}", key, e.getClass().getSimpleName());
            return supplier.get();
        }
    }

    private List<Song> shuffleAndLimit(List<Song> songs, int limit) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        List<Song> shuffled = new ArrayList<>(songs);
        Collections.shuffle(shuffled);
        return limitSongs(shuffled, limit);
    }

    private List<Song> limitSongs(List<Song> songs, int limit) {
        if (songs == null || songs.isEmpty()) {
            return Collections.emptyList();
        }
        return songs.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Song> loadPublicSongsInOrder(List<Long> orderedIds) {
        Map<Long, Song> publicSongs = filterPublicSongs(songMapper.selectBatchIds(orderedIds)).stream()
                .filter(song -> song.getId() != null)
                .collect(Collectors.toMap(Song::getId, song -> song, (first, ignored) -> first));
        if (publicSongs.size() != orderedIds.size()) {
            throw new IllegalArgumentException("歌曲不存在、已下架或当前不可公开访问");
        }
        return orderedIds.stream().map(publicSongs::get).collect(Collectors.toList());
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
                .filter(song -> CommonConstants.STATUS_NORMAL.equals(song.getStatus()))
                .filter(song -> CommonConstants.NOT_DELETED.equals(song.getDeleted()))
                .filter(song -> song.getUploaderId() == null || allowedUploaderIds.contains(song.getUploaderId()))
                .collect(Collectors.toList());
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
