package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.CreatorAlbum;
import com.haoran.music.entity.CreatorAlbumSong;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CreatorAlbumService;
import com.haoran.music.common.dto.PageQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

   
                      
                        
   
@Slf4j
@RestController
@RequestMapping("/creator/album")
public class CreatorAlbumController {

    private final CreatorAlbumService creatorAlbumService;

    public CreatorAlbumController(CreatorAlbumService creatorAlbumService) {
        this.creatorAlbumService = creatorAlbumService;
    }

       
           
       
    @ApiLog("创建专辑")
    @PostMapping
    public Result createAlbum(HttpServletRequest request,
                             @RequestParam String albumName,
                             @RequestParam Integer albumType,
                             @RequestParam(required = false) String coverUrl,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String tags,
                             @RequestParam(required = false) Integer language,
                             @RequestParam(required = false) String releaseDate,
                             @RequestParam(required = false) Integer autoCreateSong) {
        Long userId = (Long) request.getAttribute("userId");

        CreatorAlbum album = new CreatorAlbum();
        album.setAlbumName(albumName);
        album.setAlbumType(albumType);
        album.setCoverUrl(coverUrl);
        album.setDescription(description);
        album.setTags(tags);
        album.setLanguage(language != null ? language : 1);
        album.setAutoCreateSong(autoCreateSong != null ? autoCreateSong : 1);

        if (releaseDate != null && !releaseDate.isEmpty()) {
            try {
                album.setReleaseDate(java.time.LocalDate.parse(releaseDate));
                album.setIsPublishDateSet(1);
            } catch (Exception e) {
                         
            }
        }

        Long albumId = creatorAlbumService.createAlbum(userId, album);
        return Result.success(albumId);
    }

       
             
       
    @ApiLog("更新专辑")
    @PutMapping("/{id}")
    public Result updateAlbum(@PathVariable Long id,
                             HttpServletRequest request,
                             @RequestParam(required = false) String albumName,
                             @RequestParam(required = false) String coverUrl,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) String tags) {
        Long userId = (Long) request.getAttribute("userId");

        CreatorAlbum album = new CreatorAlbum();
        album.setAlbumName(albumName);
        album.setCoverUrl(coverUrl);
        album.setDescription(description);
        album.setTags(tags);

        creatorAlbumService.updateAlbum(id, userId, album);
        return Result.success();
    }

       
           
       
    @ApiLog("发布专辑")
    @PostMapping("/{id}/publish")
    public Result publishAlbum(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        creatorAlbumService.publishAlbum(id, userId);
        return Result.success();
    }

       
           
       
    @ApiLog("删除专辑")
    @DeleteMapping("/{id}")
    public Result deleteAlbum(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        creatorAlbumService.deleteAlbum(id, userId);
        return Result.success();
    }

       
             
       
    @ApiLog("获取专辑详情")
    @GetMapping("/{id}")
    public Result getAlbumDetail(@PathVariable Long id,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        CreatorAlbum album = creatorAlbumService.getAlbumDetail(id, userId);
        return Result.success(album);
    }

       
               
       
    @ApiLog("获取我的专辑列表")
    @GetMapping("/my")
    public Result getMyAlbums(HttpServletRequest request,
                             @RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        PageQuery pageQuery = new PageQuery(page, size);
        IPage<CreatorAlbum> result = creatorAlbumService.getMyAlbums(userId, pageQuery);
        return Result.success(result);
    }

       
                
       
    @ApiLog("获取专辑歌曲列表")
    @GetMapping("/{id}/songs")
    public Result getAlbumSongs(@PathVariable Long id,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        List<CreatorAlbumSong> songs = creatorAlbumService.getAlbumSongs(id, userId);
        return Result.success(songs);
    }

       
              
       
    @ApiLog("添加歌曲到专辑")
    @PostMapping("/{id}/songs")
    public Result addSongsToAlbum(@PathVariable Long id,
                                  HttpServletRequest request,
                                  @RequestBody List<Long> songIds) {
        Long userId = (Long) request.getAttribute("userId");
        creatorAlbumService.addSongsToAlbum(id, userId, songIds);
        return Result.success();
    }

       
              
       
    @ApiLog("从专辑移除歌曲")
    @DeleteMapping("/{albumId}/songs/{songId}")
    public Result removeSongFromAlbum(@PathVariable Long albumId,
                                     @PathVariable Long songId,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        creatorAlbumService.removeSongFromAlbum(albumId, songId, userId);
        return Result.success();
    }

       
                 
       
    @ApiLog("更新专辑歌曲位置")
    @PutMapping("/{albumId}/songs/{songId}")
    public Result updateSongPosition(@PathVariable Long albumId,
                                     @PathVariable Long songId,
                                     @RequestParam Integer position,
                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        creatorAlbumService.updateSongPosition(albumId, songId, position, userId);
        return Result.success();
    }

       
              
       
    @ApiLog("上传歌曲到专辑")
    @PostMapping("/{id}/upload-song")
    public Result uploadSongToAlbum(@PathVariable Long id,
                                    HttpServletRequest request,
                                    @RequestParam String songName,
                                    @RequestParam String audioUrl,
                                    @RequestParam(required = false) Integer uploadType) {
        Long userId = (Long) request.getAttribute("userId");

        CreatorAlbumSong albumSong = new CreatorAlbumSong();
        albumSong.setSongName(songName);
        albumSong.setAudioUrl(audioUrl);
        albumSong.setUploadType(uploadType != null ? uploadType : 1);

        Long songId = creatorAlbumService.uploadSongToAlbum(id, userId, albumSong);
        return Result.success(songId);
    }
}
