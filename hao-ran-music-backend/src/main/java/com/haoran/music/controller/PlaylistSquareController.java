   
                      
                       
   

package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.Playlist;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.vo.playlist.PlaylistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
          
   
@Slf4j
@RestController
@RequestMapping("/playlist-square")
public class PlaylistSquareController {

    private final PlaylistService playlistService;

    public PlaylistSquareController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

       
             
      
                        
                     
       
    @GetMapping("/featured")
    @ApiLog("获取精选歌单")
    public Result<List<PlaylistVO>> getFeaturedPlaylists(@RequestParam(defaultValue = "10") Integer limit) {
        List<PlaylistVO> playlists = playlistService.getFeaturedPlaylists(limit);
        return Result.success(playlists);
    }

       
             
      
                        
                     
       
    @GetMapping("/hot")
    @ApiLog("获取热门歌单")
    public Result<List<PlaylistVO>> getHotPlaylists(@RequestParam(defaultValue = "20") Integer limit) {
        List<PlaylistVO> playlists = playlistService.getHotPlaylists("all", limit, null);
        return Result.success(playlists);
    }

       
              
      
                           
                           
                   
       
    @GetMapping("/category/{category}")
    @ApiLog("按分类获取歌单")
    public Result<List<PlaylistVO>> getPlaylistsByCategory(@PathVariable String category,
                                                          @RequestParam(defaultValue = "20") Integer limit) {
        List<PlaylistVO> playlists = playlistService.getPlaylistsByCategory(category, limit);
        return Result.success(playlists);
    }

       
               
      
                   
       
    @GetMapping("/categories")
    @ApiLog("获取歌单分类")
    public Result<List<String>> getPlaylistCategories() {
        List<String> categories = playlistService.getPlaylistCategories();
        return Result.success(categories);
    }

       
           
      
                         
                        
                          
                     
       
    @GetMapping("/search")
    @ApiLog("搜索歌单")
    public Result<IPage<PlaylistVO>> searchPlaylists(@RequestParam String keyword,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "20") Integer size) {
        PageQuery pageQuery = new PageQuery(page, size);
        IPage<PlaylistVO> result = playlistService.searchPlaylists(keyword, pageQuery);
        return Result.success(result);
    }

       
               
      
                   
       
    @GetMapping("/overview")
    @ApiLog("获取歌单广场概览")
    public Result<Map<String, Object>> getSquareOverview() {
        Map<String, Object> overview = new HashMap<>();

               
        overview.put("featured", playlistService.getFeaturedPlaylists(6));

               
        overview.put("hot", playlistService.getHotPlaylists("all", 6, null));

               
        overview.put("latest", playlistService.getLatestPlaylists(6));

               
        overview.put("categories", playlistService.getPlaylistCategories());

        return Result.success(overview);
    }

       
                  
      
                        
                   
       
    @GetMapping("/user-created")
    @ApiLog("获取用户优秀歌单")
    public Result<List<PlaylistVO>> getUserCreatedPlaylists(@RequestParam(defaultValue = "20") Integer limit) {
        List<PlaylistVO> playlists = playlistService.getUserCreatedPlaylists(limit);
        return Result.success(playlists);
    }
}
