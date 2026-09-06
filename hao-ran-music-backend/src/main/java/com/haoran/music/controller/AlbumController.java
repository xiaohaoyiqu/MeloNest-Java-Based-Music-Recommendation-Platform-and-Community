package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.service.AlbumService;
import com.haoran.music.vo.album.AlbumVO;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

   
                      
                     
   

@RestController
@RequestMapping("/album")
public class AlbumController {

    @Resource
    private AlbumService albumService;

       
             
      
                     
                   
       
    @ApiLog("获取专辑详情")

    @GetMapping("/info/{id}")
    public Result<AlbumVO> getAlbumById(@PathVariable("id") Long id,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        AlbumVO result = albumService.getAlbumById(id, userId);
        return Result.success(result);
    }

       
               
      
                            
                                                        
                       
                        
                                         
                   
       
    @ApiLog("查询专辑列表")
    @DetectCrawler(operation = "查询专辑列表", checkReferer = true)

    @GetMapping("/page")
    public Result<IPage<AlbumVO>> pageAlbums(PageQuery pageQuery,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String language,
                                           @RequestParam(required = false) String area,
                                           @RequestParam(required = false) String genre,
                                           @RequestParam(required = false) String sortBy,
                                           @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<AlbumVO> result = albumService.pageAlbums(pageQuery, keyword, language, area, genre, sortBy, userId);
        return Result.success(result);
    }

       
               
      
                           
                   
       
    @ApiLog("获取歌手专辑列表")

    @GetMapping("/artist/{artistId}")
    public Result<List<AlbumVO>> getAlbumsByArtist(@PathVariable("artistId") Long artistId,
                                                  @RequestAttribute(value = "userId", required = false) Long userId) {
        List<AlbumVO> result = albumService.getAlbumsByArtist(artistId, userId);
        return Result.success(result);
    }

       
              
      
                            
                    
       
    @ApiLog("获取新专辑列表")

    @GetMapping("/new")
    public Result<IPage<AlbumVO>> getNewAlbums(PageQuery pageQuery,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<AlbumVO> result = albumService.getNewAlbums(pageQuery, userId);
        return Result.success(result);
    }

       
             
      
                                             
                        
                     
       
    @ApiLog("获取热门专辑")

    @GetMapping("/hot")
    public Result<List<AlbumVO>> getHotAlbums(@RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<AlbumVO> result = albumService.getHotAlbums(type, limit, userId);
        return Result.success(result);
    }

       
           
      
                          
                 
       
    @ApiLog("收藏专辑")

    @PostMapping("/favorite/{albumId}")
    public Result<Void> favoriteAlbum(@PathVariable("albumId") Long albumId,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        albumService.favoriteAlbum(userId, albumId);
        return Result.success();
    }

       
             
      
                          
                 
       
    @ApiLog("取消收藏专辑")

    @DeleteMapping("/favorite/{albumId}")
    public Result<Void> unfavoriteAlbum(@PathVariable("albumId") Long albumId,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        albumService.unfavoriteAlbum(userId, albumId);
        return Result.success();
    }

       
                  
      
                         
                      
       
    @ApiLog("获取收藏专辑列表")

    @GetMapping("/favorites")
    public Result<List<AlbumVO>> getUserFavoriteAlbums(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<AlbumVO> result = albumService.getUserFavoriteAlbums(userId);
        return Result.success(result);
    }

       
               
      
                          
                   
       
    @ApiLog("获取专辑歌曲列表")

    @GetMapping("/{albumId}/songs")
    public Result<List<SongVO>> getAlbumSongs(@PathVariable("albumId") Long albumId,
                                           @RequestAttribute(value = "userId", required = false) Long userId) {
        List<SongVO> result = albumService.getAlbumSongs(albumId, userId);
        return Result.success(result);
    }

       
               
      
                          
                          
                     
       
    @ApiLog("获取相似专辑推荐")
    @GetMapping("/{albumId}/similar")
    public Result<List<AlbumVO>> getSimilarAlbums(@PathVariable("albumId") Long albumId,
                                                  @RequestParam(defaultValue = "10") Integer limit) {
        List<AlbumVO> result = albumService.getSimilarAlbums(albumId, limit);
        return Result.success(result);
    }

       
                   
      
                          
                          
                        
       
    @ApiLog("获取艺术家其他专辑推荐")
    @GetMapping("/{albumId}/artist-albums")
    public Result<List<AlbumVO>> getArtistOtherAlbums(@PathVariable("albumId") Long albumId,
                                                      @RequestParam(defaultValue = "10") Integer limit) {
        List<AlbumVO> result = albumService.getArtistOtherAlbums(albumId, limit);
        return Result.success(result);
    }
}
