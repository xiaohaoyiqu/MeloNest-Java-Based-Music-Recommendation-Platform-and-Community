package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.SpiderRateLimiter;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.entity.Song;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.AlbumMapper;
import com.haoran.music.mapper.ArtistMapper;
import com.haoran.music.mapper.SongMapper;
import com.haoran.music.spider.MusicSpiderService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.HashMap;







@Slf4j
@RestController
@RequestMapping("/spider")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class SpiderController {

    @Resource
    private MusicSpiderService musicSpiderService;

    @Resource
    private SongMapper songMapper;

    @Resource
    private ArtistMapper artistMapper;

    @Resource
    private AlbumMapper albumMapper;

    @Resource
    private SpiderRateLimiter spiderRateLimiter;




    @ApiLog("爬取歌曲数据")
    @PostMapping("/songs")
    public Result<String> spiderSongs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "false") Boolean save) {


        if (!spiderRateLimiter.checkRateLimit()) {
            return Result.error(429, "今日爬取次数已达上限（2000次），请明天再试");
        }

        log.info("开始爬取歌曲，关键词: {}, 限制: {}, 保存: {}", keyword, limit, save);

        try {
            List<Song> songs = musicSpiderService.spiderSongs(keyword, limit);

            if (save) {
                int savedCount = 0;
                for (Song song : songs) {
                    LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Song::getName, song.getName())
                            .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

                    Song existing = songMapper.selectOne(wrapper);
                    if (existing == null) {
                        songMapper.insert(song);
                        savedCount++;
                    }
                }
                return Result.success("成功爬取" + songs.size() + "首歌曲，保存" + savedCount + "首新歌曲");
            }

            return Result.success("成功爬取" + songs.size() + "首歌曲");

        } catch (Exception e) {
            log.error("event=spider_song_crawl_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "爬取失败，请稍后重试");
        }
    }




    @ApiLog("爬取歌手数据")
    @PostMapping("/artists")
    public Result<String> spiderArtists(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "false") Boolean save) {

        if (!spiderRateLimiter.checkRateLimit()) {
            return Result.error(429, "今日爬取次数已达上限（2000次），请明天再试");
        }

        log.info("开始爬取歌手，关键词: {}, 限制: {}, 保存: {}", keyword, limit, save);

        try {
            List<Artist> artists = musicSpiderService.spiderArtists(keyword, limit);

            if (save) {
                int savedCount = 0;
                for (Artist artist : artists) {
                    LambdaQueryWrapper<Artist> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Artist::getName, artist.getName())
                            .eq(Artist::getDeleted, CommonConstants.NOT_DELETED);

                    Artist existing = artistMapper.selectOne(wrapper);
                    if (existing == null) {
                        artistMapper.insert(artist);
                        savedCount++;
                    }
                }
                return Result.success("成功爬取" + artists.size() + "位歌手，保存" + savedCount + "位新歌手");
            }

            return Result.success("成功爬取" + artists.size() + "位歌手");

        } catch (Exception e) {
            log.error("event=spider_artist_crawl_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "爬取失败，请稍后重试");
        }
    }




    @ApiLog("爬取专辑数据")
    @PostMapping("/albums")
    public Result<String> spiderAlbums(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestParam(defaultValue = "false") Boolean save) {

        if (!spiderRateLimiter.checkRateLimit()) {
            return Result.error(429, "今日爬取次数已达上限（2000次），请明天再试");
        }

        log.info("开始爬取专辑，关键词: {}, 限制: {}, 保存: {}", keyword, limit, save);

        try {
            List<Album> albums = musicSpiderService.spiderAlbums(null, keyword, limit);

            if (save) {
                int savedCount = 0;
                for (Album album : albums) {
                    LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Album::getName, album.getName())
                            .eq(Album::getDeleted, CommonConstants.NOT_DELETED);

                    Album existing = albumMapper.selectOne(wrapper);
                    if (existing == null) {
                        albumMapper.insert(album);
                        savedCount++;
                    }
                }
                return Result.success("成功爬取" + albums.size() + "张专辑，保存" + savedCount + "张新专辑");
            }

            return Result.success("成功爬取" + albums.size() + "张专辑");

        } catch (Exception e) {
            log.error("event=spider_album_crawl_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "爬取失败，请稍后重试");
        }
    }




    @ApiLog("根据歌手ID爬取歌曲")
    @PostMapping("/songs/artist/{artistId}")
    public Result<String> spiderSongsByArtist(
            @PathVariable("artistId") Long artistId,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "false") Boolean save) {

        if (!spiderRateLimiter.checkRateLimit()) {
            return Result.error(429, "今日爬取次数已达上限（2000次），请明天再试");
        }

        log.info("开始爬取歌手歌曲，歌手ID: {}, 限制: {}, 保存: {}", artistId, limit, save);

        try {
            List<Song> songs = musicSpiderService.spiderSongsByArtist(artistId, limit);

            if (save) {
                int savedCount = 0;
                for (Song song : songs) {
                    LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Song::getName, song.getName())
                            .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

                    Song existing = songMapper.selectOne(wrapper);
                    if (existing == null) {
                        songMapper.insert(song);
                        savedCount++;
                    }
                }
                return Result.success("成功爬取" + songs.size() + "首歌曲，保存" + savedCount + "首新歌曲");
            }

            return Result.success("成功爬取" + songs.size() + "首歌曲");

        } catch (Exception e) {
            log.error("event=spider_artist_song_crawl_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "爬取失败，请稍后重试");
        }
    }




    @ApiLog("根据专辑ID爬取歌曲")
    @PostMapping("/songs/album/{albumId}")
    public Result<String> spiderSongsByAlbum(
            @PathVariable("albumId") Long albumId,
            @RequestParam(defaultValue = "false") Boolean save) {

        if (!spiderRateLimiter.checkRateLimit()) {
            return Result.error(429, "今日爬取次数已达上限（2000次），请明天再试");
        }

        log.info("开始爬取专辑歌曲，专辑ID: {}, 保存: {}", albumId, save);

        try {
            List<Song> songs = musicSpiderService.spiderSongsByAlbum(albumId);

            if (save) {
                int savedCount = 0;
                for (Song song : songs) {
                    LambdaQueryWrapper<Song> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Song::getName, song.getName())
                            .eq(Song::getAlbumId, albumId)
                            .eq(Song::getDeleted, CommonConstants.NOT_DELETED);

                    Song existing = songMapper.selectOne(wrapper);
                    if (existing == null) {
                        songMapper.insert(song);
                        savedCount++;
                    }
                }
                return Result.success("成功爬取" + songs.size() + "首歌曲，保存" + savedCount + "首新歌曲");
            }

            return Result.success("成功爬取" + songs.size() + "首歌曲");

        } catch (Exception e) {
            log.error("event=spider_album_song_crawl_failed errorType={}", e.getClass().getSimpleName());
            return Result.error(500, "爬取失败，请稍后重试");
        }
    }




    @GetMapping("/stats")
    public Result<Map<String, Object>> getSpiderStats() {
        int currentCount = spiderRateLimiter.getCurrentCount();
        int remainingCount = spiderRateLimiter.getRemainingCount();

        Map<String, Object> stats = new HashMap<>();
        stats.put("dailyLimit", 2000);
        stats.put("currentCount", currentCount);
        stats.put("remainingCount", remainingCount);

        return Result.success(stats);
    }
}
