   
                      
                      
   

package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.Song;
import com.haoran.music.entity.Album;
import com.haoran.music.entity.Artist;
import com.haoran.music.service.SongService;
import com.haoran.music.service.AlbumService;
import com.haoran.music.service.ArtistService;
import com.haoran.music.service.MVService;
import com.haoran.music.service.RankingSnapshotQueryService;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.vo.album.AlbumVO;
import com.haoran.music.vo.artist.ArtistVO;
import com.haoran.music.vo.mv.MVVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
         
   
@Slf4j
@RestController
@RequestMapping("/ranking")
public class RankingController {

    private static final int MAX_RANKING_LIMIT = 100;

    private final SongService songService;
    private final AlbumService albumService;
    private final ArtistService artistService;
    private final MVService mvService;
    private final RankingSnapshotQueryService rankingSnapshotQueryService;

    public RankingController(SongService songService,
                            AlbumService albumService,
                            ArtistService artistService,
                            MVService mvService,
                            RankingSnapshotQueryService rankingSnapshotQueryService) {
        this.songService = songService;
        this.albumService = albumService;
        this.artistService = artistService;
        this.mvService = mvService;
        this.rankingSnapshotQueryService = rankingSnapshotQueryService;
    }

       
              
      
                                                    
                        
                   
       
    @GetMapping("/songs")
    @ApiLog("获取歌曲排行榜")
    public Result<List<SongVO>> getSongRanking(@RequestParam(defaultValue = "hot") String type,
                                               @RequestParam(defaultValue = "100") Integer limit) {
        int safeLimit = normalizeLimit(limit, 100);
        List<SongVO> songs;

        switch (type) {
            case "hot":
                songs = getHotSongRanking(safeLimit);
                break;
            case "new":
                songs = songService.getNewSongs(safeLimit);
                break;
            case "rise":
                songs = songService.getRisingSongs(safeLimit);
                break;
            default:
                return Result.error(400, "不支持的歌曲榜单类型");
        }

        return Result.success(songs);
    }

       
              
      
                                         
                        
                   
       
    @GetMapping("/albums")
    @ApiLog("获取专辑排行榜")
    public Result<List<AlbumVO>> getAlbumRanking(@RequestParam(defaultValue = "hot") String type,
                                                  @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = normalizeLimit(limit, 50);
        List<AlbumVO> albums;

        if ("new".equals(type)) {
            albums = albumService.getNewAlbums(safeLimit);
        } else if ("hot".equals(type)) {
            albums = albumService.getHotAlbums("all", safeLimit, null);
        } else {
            return Result.error(400, "不支持的专辑榜单类型");
        }

        return Result.success(albums);
    }

       
              
      
                        
                   
       
    @GetMapping("/artists")
    @ApiLog("获取歌手排行榜")
    public Result<List<ArtistVO>> getArtistRanking(@RequestParam(defaultValue = "50") Integer limit) {
        List<ArtistVO> artists = artistService.getHotArtists(normalizeLimit(limit, 50), null);
        return Result.success(artists);
    }

       
              
      
                                            
                        
                   
       
    @GetMapping("/mvs")
    @ApiLog("获取MV排行榜")
    public Result<List<MVVO>> getMvRanking(@RequestParam(defaultValue = "hot") String type,
                                           @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = normalizeLimit(limit, 50);
        List<MVVO> mvs;

        if ("new".equals(type)) {
            mvs = mvService.getNewestMVs(safeLimit, null);
        } else if ("hot".equals(type)) {
            mvs = mvService.getHotMVs(safeLimit, null);
        } else {
            return Result.error(400, "不支持的MV榜单类型");
        }

        return Result.success(mvs);
    }

       
              
      
                        
                        
                   
       
    @GetMapping("/genre/{genre}")
    @ApiLog("获取分类排行榜")
    public Result<List<SongVO>> getGenreRanking(@PathVariable String genre,
                                                @RequestParam(defaultValue = "50") Integer limit) {
        if (genre == null || genre.trim().isEmpty() || genre.length() > 40) {
            return Result.error(400, "音乐风格参数不合法");
        }
        List<SongVO> songs = songService.getSongsByGenre(genre.trim(), normalizeLimit(limit, 50));
        return Result.success(songs);
    }

       
                
      
                    
       
    @GetMapping("/overview")
    @ApiLog("获取排行榜概览")
    public Result<Map<String, Object>> getRankingOverview() {
        Map<String, Object> overview = new HashMap<>();

                   
        overview.put("hotSongs", getHotSongRanking(10));

                   
        overview.put("newSongs", songService.getNewSongs(10));

                    
        overview.put("hotAlbums", albumService.getHotAlbums("all", 10, null));

                    
        overview.put("hotArtists", artistService.getHotArtists(10, null));

                     
        overview.put("hotMVs", mvService.getHotMVs(10, null));

        return Result.success(overview);
    }

       
                
      
                        
                      
       
    @GetMapping("/artists/by-category")
    @ApiLog("获取歌手分类排行榜")
    public Result<Map<String, List<ArtistVO>>> getArtistRankingByCategory(
            @RequestParam(defaultValue = "10") Integer limit) {

        int safeLimit = normalizeLimit(limit, 10);

        Map<String, List<ArtistVO>> result = new HashMap<>();

                
        result.put("chinese_male", artistService.getArtistsByCategory("chinese_male", safeLimit));

                
        result.put("chinese_female", artistService.getArtistsByCategory("chinese_female", safeLimit));

               
        result.put("western", artistService.getArtistsByCategory("western", safeLimit));

               
        result.put("asian", artistService.getArtistsByCategory("asian", safeLimit));

        return Result.success(result);
    }

       
               
      
                                                   
                        
                    
       
    @GetMapping("/creators")
    @ApiLog("获取创作者排行榜")
    public Result<List<ArtistVO>> getCreatorRanking(@RequestParam(defaultValue = "hot") String type,
                                                    @RequestParam(defaultValue = "50") Integer limit) {
        int safeLimit = normalizeLimit(limit, 50);
        List<ArtistVO> creators;
        
        switch (type) {
            case "hot":
                creators = artistService.getHotCreators(safeLimit, null);
                break;
            case "new":
                creators = artistService.getNewCreators(safeLimit);
                break;
            case "active":
                creators = artistService.getActiveCreators(safeLimit);
                break;
            default:
                return Result.error(400, "不支持的创作者榜单类型");
        }
        
        return Result.success(creators);
    }

       
              
      
                                                       
                        
                   
       
    @GetMapping("/language-songs")
    @ApiLog("获取语言排行榜")
    public Result<List<SongVO>> getLanguageSongsRanking(@RequestParam(defaultValue = "zh") String language,
                                                         @RequestParam(defaultValue = "50") Integer limit) {
        if (!java.util.Arrays.asList("zh", "en", "ko", "ja").contains(language)) {
            return Result.error(400, "不支持的语言榜单类型");
        }
        List<SongVO> songs = songService.getSongsByLanguage(language, normalizeLimit(limit, 50));
        return Result.success(songs);
    }

    private int normalizeLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        return Math.max(1, Math.min(limit, MAX_RANKING_LIMIT));
    }

       
                                  
      
                         
                      
  
    private List<SongVO> getHotSongRanking(int limit) {
        List<Long> rankedSongIds = rankingSnapshotQueryService.getActiveHotSongIds(limit);
        if (rankedSongIds.isEmpty()) {
            return songService.getHotSongs("all", limit, null);
        }
        return songService.getPublicSongsByIdsInOrder(rankedSongIds);
    }
}
