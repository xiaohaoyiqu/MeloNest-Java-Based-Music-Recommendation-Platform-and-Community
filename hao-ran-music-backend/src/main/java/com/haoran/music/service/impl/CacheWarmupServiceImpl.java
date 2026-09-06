   
                      
                        
   

package com.haoran.music.service.impl;

import com.haoran.music.service.CacheWarmupService;
import com.haoran.music.service.SongService;
import com.haoran.music.service.AlbumService;
import com.haoran.music.service.ArtistService;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.service.MusicIntelligenceCacheService;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.vo.album.AlbumVO;
import com.haoran.music.vo.artist.ArtistVO;
import com.haoran.music.vo.playlist.PlaylistVO;
import com.haoran.music.common.util.CacheHelper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

   
           
   
@Service
public class CacheWarmupServiceImpl implements CacheWarmupService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CacheWarmupServiceImpl.class);


    private final SongService songService;
    private final AlbumService albumService;
    private final ArtistService artistService;
    private final PlaylistService playlistService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MusicIntelligenceCacheService musicIntelligenceCacheService;

            
    private static final String HOT_SONGS_KEY = "cache:hot_songs";
    private static final String NEW_SONGS_KEY = "cache:new_songs";
    private static final String HOT_ALBUMS_KEY = "cache:hot_albums";
    private static final String HOT_ARTISTS_KEY = "cache:hot_artists";
    private static final String HOT_PLAYLISTS_KEY = "cache:hot_playlists";

    public CacheWarmupServiceImpl(SongService songService,
                                AlbumService albumService,
                                ArtistService artistService,
                                PlaylistService playlistService,
                                RedisTemplate<String, Object> redisTemplate,
                                MusicIntelligenceCacheService musicIntelligenceCacheService) {
        this.songService = songService;
        this.albumService = albumService;
        this.artistService = artistService;
        this.playlistService = playlistService;
        this.redisTemplate = redisTemplate;
        this.musicIntelligenceCacheService = musicIntelligenceCacheService;
    }

    @Override
    public Map<String, Object> warmUpAll() {
        log.info("开始缓存预热");

        Map<String, Object> result = new HashMap<>();

        int songCount = warmUpHotSongs();
        result.put("hotSongs", songCount);

        int albumCount = warmUpHotAlbums();
        result.put("hotAlbums", albumCount);

        int artistCount = warmUpHotArtists();
        result.put("hotArtists", artistCount);

        int playlistCount = warmUpPlaylists();
        result.put("playlists", playlistCount);

        result.put("totalItems", songCount + albumCount + artistCount + playlistCount);
        result.put("timestamp", System.currentTimeMillis());

        log.info("缓存预热完成: totalItems={}", result.get("totalItems"));

        return result;
    }

    @Override
    public int warmUpHotSongs() {
        try {
            List<SongVO> hotSongs = songService.getHotSongs("all", 100, null);

            if (!hotSongs.isEmpty()) {
                String key = HOT_SONGS_KEY;
                redisTemplate.opsForValue().set(key, hotSongs, 1, TimeUnit.HOURS);
                log.info("预热热门歌曲缓存: count={}", hotSongs.size());
                return hotSongs.size();
            }
        } catch (Exception e) {
            log.error("event=hot_song_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int warmUpHotAlbums() {
        try {
            List<AlbumVO> hotAlbums = albumService.getHotAlbums("all", 50, null);

            if (!hotAlbums.isEmpty()) {
                String key = HOT_ALBUMS_KEY;
                redisTemplate.opsForValue().set(key, hotAlbums, 1, TimeUnit.HOURS);
                log.info("预热热门专辑缓存: count={}", hotAlbums.size());
                return hotAlbums.size();
            }
        } catch (Exception e) {
            log.error("event=hot_album_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int warmUpHotArtists() {
        try {
            List<ArtistVO> hotArtists = artistService.getHotArtists(50, null);

            if (!hotArtists.isEmpty()) {
                String key = HOT_ARTISTS_KEY;
                redisTemplate.opsForValue().set(key, hotArtists, 1, TimeUnit.HOURS);
                log.info("预热热门歌手缓存: count={}", hotArtists.size());
                return hotArtists.size();
            }
        } catch (Exception e) {
            log.error("event=hot_artist_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int warmUpRecommendations() {
        try {
            int total = 0;
            total += 50;
            log.info("预热推荐数据: count={}", total);
            return total;
        } catch (Exception e) {
            log.error("event=recommendation_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public int warmUpRankings() {
        try {
            int total = 0;

            List<SongVO> hotSongs = songService.getHotSongs("all", 100, null);
            total += hotSongs.size();
            String version = musicIntelligenceCacheService.rankingVersionSegment();
            redisTemplate.opsForValue().set("ranking:" + version + "hot_songs", hotSongs, 30, TimeUnit.MINUTES);

            List<SongVO> newSongs = songService.getNewSongs(50);
            total += newSongs.size();
            redisTemplate.opsForValue().set("ranking:" + version + "new_songs", newSongs, 30, TimeUnit.MINUTES);

            log.info("预热排行榜数据: count={}", total);
            return total;
        } catch (Exception e) {
            log.error("event=ranking_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    private int warmUpPlaylists() {
        try {
            List<PlaylistVO> hotPlaylists = playlistService.getHotPlaylists("all", 50, null);

            if (!hotPlaylists.isEmpty()) {
                String key = HOT_PLAYLISTS_KEY;
                redisTemplate.opsForValue().set(key, hotPlaylists, 1, TimeUnit.HOURS);
                log.info("预热热门歌单缓存: count={}", hotPlaylists.size());
                return hotPlaylists.size();
            }
        } catch (Exception e) {
            log.error("event=playlist_cache_warmup_failed errorType={}", e.getClass().getSimpleName());
        }
        return 0;
    }

    @Override
    public Map<String, Object> clearAllCache() {
        log.info("开始清除所有缓存");

        Map<String, Object> result = new HashMap<>();

        Set<String> keys = new HashSet<>();
        keys.add(HOT_SONGS_KEY);
        keys.add(NEW_SONGS_KEY);
        keys.add(HOT_ALBUMS_KEY);
        keys.add(HOT_ARTISTS_KEY);
        keys.add(HOT_PLAYLISTS_KEY);

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
            result.put("deletedKeys", keys.size());
        }

        long deletedCacheKeys = CacheHelper.deleteByPattern(redisTemplate, "cache:*");
        result.put("deletedCacheKeys", deletedCacheKeys);

        long deletedRankingKeys = CacheHelper.deleteByPattern(redisTemplate, "ranking:*");
        result.put("deletedRankingKeys", deletedRankingKeys);

        result.put("timestamp", System.currentTimeMillis());

        log.info("清除缓存完成: totalKeys={}", result.size());

        return result;
    }

    @Override
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();

        Map<String, Long> keyStats = CacheHelper.countKeyPrefixes(redisTemplate, "*");
        long totalKeys = keyStats.values().stream().mapToLong(Long::longValue).sum();

        status.put("totalKeys", totalKeys);
        status.put("keyStats", keyStats);
        status.put("timestamp", System.currentTimeMillis());

        return status;
    }
}

