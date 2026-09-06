package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.FileUploadUtil;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.dto.playlist.PlaylistCopyMoveResult;
import com.haoran.music.dto.playlist.PlaylistCreateDTO;
import com.haoran.music.dto.playlist.PlaylistOrderDTO;
import com.haoran.music.dto.playlist.PlaylistUpdateDTO;
import com.haoran.music.entity.User;
import com.haoran.music.service.PlaylistService;
import com.haoran.music.service.UserService;
import com.haoran.music.vo.playlist.PlaylistVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

   
                      
                     
   
@Slf4j
@RestController
@RequestMapping("/playlist")
public class PlaylistController {

    @Resource
    private PlaylistService playlistService;

    @Resource
    private FileUploadUtil fileUploadUtil;

    @Resource
    private UserService userService;

       
                   
       
    @ApiLog("获取歌单详情")
    @DetectCrawler(operation = "获取歌单详情", checkReferer = true, riskThreshold = 60)
    @GetMapping("/info/{id}")
    public Result<PlaylistVO> getPlaylistById(@PathVariable("id") Long id,
                                           @RequestAttribute(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                           @RequestParam(value = "size", required = false, defaultValue = "50") Integer size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String language,
                                           @RequestParam(defaultValue = "default") String sortBy) {
        PlaylistVO result = playlistService.getPlaylistById(id, userId, page, size, keyword, language, sortBy);
        return Result.success(result);
    }

    public Result<PlaylistVO> getPlaylistById(Long id,
                                              @RequestAttribute(value = "userId", required = false) Long userId,
                                              Integer page, Integer size) {
        return Result.success(playlistService.getPlaylistById(id, userId, page, size));
    }

    @ApiLog("查询歌单列表")
    @DetectCrawler(operation = "查询歌单列表", checkReferer = true, checkBehavior = false)
    @GetMapping("/page")
    public Result<IPage<PlaylistVO>> pagePlaylists(PageQuery pageQuery,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String language,
                                                     @RequestParam(required = false) List<String> languages,
                                                     @RequestParam(defaultValue = "any") String languageMode,
                                                     @RequestParam(required = false) String tag,
                                                     @RequestParam(required = false) String category,
                                                     @RequestParam(required = false) Integer minSongCount,
                                                     @RequestParam(required = false) Integer maxSongCount,
                                                     @RequestParam(required = false) String paymentType,
                                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<PlaylistVO> result = playlistService.pagePlaylists(pageQuery, keyword, language, languages,
                languageMode, tag, category, minSongCount, maxSongCount, paymentType, userId);
        return Result.success(result);
    }

    @ApiLog("获取用户歌单列表")
    @GetMapping("/my")
    public Result<List<PlaylistVO>> getUserPlaylists(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<PlaylistVO> result = playlistService.getUserPlaylists(userId);
        return Result.success(result);
    }

    @ApiLog("获取收藏歌单")
    @GetMapping("/favorite")
    public Result<PlaylistVO> getFavoritePlaylist(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        PlaylistVO result = playlistService.getFavoritePlaylist(userId);
        return Result.success(result);
    }


    @ApiLog("获取用户收藏的歌单列表")
    @GetMapping("/favorites")
    public Result<List<PlaylistVO>> getFavoritePlaylists(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<PlaylistVO> result = playlistService.getFavoritePlaylists(userId);
        return Result.success(result);
    }

    @ApiLog("获取用户全部歌单（创建的+收藏的）")
    @GetMapping("/all")
    public Result<List<PlaylistVO>> getAllUserPlaylists(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<PlaylistVO> result = playlistService.getAllUserPlaylists(userId);
        return Result.success(result);
    }

    @ApiLog("上传歌单封面")
    @PostMapping("/cover/upload")
    public Result<String> uploadCover(@RequestParam("file") MultipartFile file,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            User user = userService.getById(userId);
            UserAccountStatusUtil.requireCanInteract(user, "上传歌单封面");
            String username = user.getNickname() != null && !user.getNickname().isEmpty()
                    ? user.getNickname() : user.getUsername();
            String url = fileUploadUtil.uploadPlaylistCover(file, userId, username);
            return Result.successData(url);
        } catch (IOException e) {
            log.error("event=playlist_cover_upload_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return Result.error(500, "封面上传失败，请稍后重试");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    @ApiLog("创建歌单")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "createPlaylist",
               message = "歌单创建过于频繁，请稍后再试")
    @PostMapping
    public Result<Long> createPlaylist(@RequestBody @Valid PlaylistCreateDTO dto,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Long result = playlistService.createPlaylist(userId, dto);
        return Result.success(result);
    }

    @ApiLog("更新歌单")
    @PutMapping
    public Result<Void> updatePlaylist(@RequestBody @Valid PlaylistUpdateDTO dto,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        playlistService.updatePlaylist(userId, dto);
        return Result.success();
    }

    @ApiLog("删除歌单")
    @DeleteMapping("/{id}")
    public Result<Void> deletePlaylist(@PathVariable("id") Long id,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        playlistService.deletePlaylist(userId, id);
        return Result.success();
    }

    @ApiLog("添加歌曲到歌单")
    @PostMapping("/{id}/songs")
    public Result<Integer> addSongsToPlaylist(@PathVariable("id") Long id,
                                           @RequestBody List<Long> songIds,
                                           @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Integer result = playlistService.addSongsToPlaylist(userId, id, songIds);
        return Result.success(result);
    }

    @ApiLog("从歌单移除歌曲")
    @DeleteMapping("/{id}/songs")
    public Result<Integer> removeSongsFromPlaylist(@PathVariable("id") Long id,
                                              @RequestBody List<Long> songIds,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Integer result = playlistService.removeSongsFromPlaylist(userId, id, songIds);
        return Result.success(result);
    }

    @ApiLog("从歌单移除单首歌曲")
    @DeleteMapping("/{playlistId}/songs/{songId}")
    public Result<Void> removeSongFromPlaylist(@PathVariable("playlistId") Long playlistId,
                                        @PathVariable("songId") Long songId,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        List<Long> songIds = java.util.Collections.singletonList(songId);
        playlistService.removeSongsFromPlaylist(userId, playlistId, songIds);
        return Result.success();
    }

    @ApiLog("收藏歌单")
    @PostMapping("/{id}/favorite")
    public Result<Void> favoritePlaylist(@PathVariable("id") Long id,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        playlistService.favoritePlaylist(userId, id);
        return Result.success();
    }

    @ApiLog("取消收藏歌单")
    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavoritePlaylist(@PathVariable("id") Long id,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        playlistService.unfavoritePlaylist(userId, id);
        return Result.success();
    }

    @ApiLog("获取热门歌单")
    @GetMapping("/hot")
    public Result<List<PlaylistVO>> getHotPlaylists(@RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<PlaylistVO> result = playlistService.getHotPlaylists(type, limit, userId);
        return Result.success(result);
    }

       
                 
      
                                    
                                     
                                     
                      
       
    @ApiLog("复制歌曲到歌单")
    @PostMapping("/{sourcePlaylistId}/copy/{targetPlaylistId}")
    public Result<PlaylistCopyMoveResult> copySongsToPlaylist(@PathVariable("sourcePlaylistId") Long sourcePlaylistId,
                                              @PathVariable("targetPlaylistId") Long targetPlaylistId,
                                              @RequestBody List<Long> songIds,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        PlaylistCopyMoveResult result = playlistService.copySongsToPlaylist(userId, sourcePlaylistId, songIds, targetPlaylistId);
        return Result.success(result);
    }

       
                 
      
                                    
                                     
                                     
                      
       
    @ApiLog("移动歌曲到歌单")
    @PostMapping("/{sourcePlaylistId}/move/{targetPlaylistId}")
    public Result<PlaylistCopyMoveResult> moveSongsToPlaylist(@PathVariable("sourcePlaylistId") Long sourcePlaylistId,
                                              @PathVariable("targetPlaylistId") Long targetPlaylistId,
                                              @RequestBody List<Long> songIds,
                                              @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        PlaylistCopyMoveResult result = playlistService.moveSongsToPlaylist(userId, sourcePlaylistId, songIds, targetPlaylistId);
        return Result.success(result);
    }

       
               
      
                                          
                   
       
    @ApiLog("更新歌单歌曲顺序")
    @PutMapping("/order")
    public Result<Void> updatePlaylistOrder(@Valid @RequestBody PlaylistOrderDTO payload,
                                           @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        playlistService.updateSongOrders(userId, payload.getPlaylistId(), payload.getSongIds(),
                payload.getExpectedOrderVersion());
        return Result.success();
    }

       
               
                                    
                        
                   
       
    @ApiLog("更新歌单付费设置")
    @PutMapping("/paid-settings")
    public Result<Boolean> updatePaidSettings(@RequestAttribute(value = "userId", required = false) Long userId,
                                           @RequestBody @Valid com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = playlistService.updatePaidSettings(userId, dto);
        return Result.success(result);
    }

       
             
                                    
                        
                   
       
    @ApiLog("设为付费歌单")
    @PostMapping("/set-paid")
    public Result<Boolean> setPlaylistPaid(@RequestAttribute(value = "userId", required = false) Long userId,
                                          @RequestBody @Valid com.haoran.music.dto.playlist.PlaylistPaidSettingsDTO dto) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = playlistService.setPlaylistPaid(userId, dto);
        return Result.success(result);
    }

       
                 
      
                           
                     
       
    @ApiLog("获取协作歌单数量")
    @GetMapping("/collaborate-count")
    public Result<Integer> getCollaboratePlaylistCount(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Integer count = playlistService.getCollaboratePlaylistCount(userId);
        return Result.success(count);
    }

}
