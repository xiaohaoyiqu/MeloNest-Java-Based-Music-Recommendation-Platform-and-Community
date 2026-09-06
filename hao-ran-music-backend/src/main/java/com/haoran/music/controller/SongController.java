package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.aspect.DetectCrawler;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.SongResourceRequestAddDTO;
import com.haoran.music.dto.SongResourceRequestHandleDTO;
import com.haoran.music.dto.song.SongRatingDTO;
import com.haoran.music.dto.song.SongVO;
import com.haoran.music.entity.SongLike;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.SongDownloadService;
import com.haoran.music.service.SongLikeService;
import com.haoran.music.service.SongRatingService;
import com.haoran.music.service.SongResourceRequestService;
import com.haoran.music.service.SongService;
import com.haoran.music.vo.song.SongResourceRequestVO;
import com.haoran.music.vo.song.SongRatingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

   
                      
                                                                                                                       
   
@Slf4j
@RestController
@RequestMapping("/song")
public class SongController {

    @Resource
    private SongService songService;

    @Resource
    private SongLikeService songLikeService;

    @Resource
    private SongRatingService songRatingService;

    @Resource
    private SongDownloadService songDownloadService;

    @Autowired
    private SongResourceRequestService songResourceRequestService;

                                                       

       
             
       
    @ApiLog("获取歌曲详情")
    @GetMapping("/info/{id}")
    public Result<SongVO> getSongById(@PathVariable("id") Long id,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        SongVO result = songService.getSongById(id, userId);
        return Result.success(result);
    }

       
               
       
    @ApiLog("查询歌曲列表")
    @DetectCrawler(operation = "查询歌曲列表", checkReferer = true, checkBehavior = false)
    @GetMapping("/page")
    public Result<IPage<SongVO>> pageSongs(PageQuery pageQuery,
                                          @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<SongVO> result = songService.pageSongs(pageQuery, userId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("获取新歌列表")
    @DetectCrawler(operation = "获取新歌列表", checkReferer = true)
    @GetMapping("/new")
    public Result<IPage<SongVO>> getNewSongs(PageQuery pageQuery,
                                             @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<SongVO> result = songService.getNewSongs(pageQuery, userId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("获取热门歌曲")
    @DetectCrawler(operation = "获取热门歌曲", checkReferer = true)
    @GetMapping("/hot")
    public Result<List<SongVO>> getHotSongs(@RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "20") Integer limit,
                                             @RequestAttribute(value = "userId", required = false) Long userId) {
        List<SongVO> result = songService.getHotSongs(type, limit, userId);
        return Result.success(result);
    }

       
           
       
    @ApiLog("记录歌曲播放")
    @PostMapping("/play/{songId}")
    public Result<Void> recordPlay(@PathVariable("songId") Long songId,
                                  @RequestAttribute(value = "userId", required = false) Long userId,
                                  @RequestParam(defaultValue = "standard") String quality) {
        return Result.error(410, "旧播放计数入口已停用，请使用带事件ID和有效进度的听歌事件");
    }

       
                
                     
       
    @ApiLog(value = "获取歌曲播放URL", logReturn = false)
    @DetectCrawler(operation = "获取播放链接", checkReferer = true, riskThreshold = 50)
    @GetMapping("/url/{songId}")
    public Result<String> getPlayUrl(@PathVariable("songId") Long songId,
                                     @RequestParam(defaultValue = "standard") String quality,
                                     @RequestAttribute(value = "userId", required = false) Long userId,
                                     HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        String url = songService.getPlayUrl(songId, quality, userId);
        return Result.successData(url);
    }

       
                                              
       
    @ApiLog(value = "获取歌曲试听URL", logReturn = false)
    @DetectCrawler(operation = "获取歌曲试听链接", checkReferer = true, riskThreshold = 50)
    @GetMapping("/preview/{songId}")
    public Result<String> getPreviewUrl(@PathVariable("songId") Long songId,
                                        @RequestAttribute(value = "userId", required = false) Long userId,
                                        HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return Result.successData(songService.getPreviewUrl(songId, userId));
    }

       
             
                  
       
    @ApiLog(value = "流式播放歌曲", logArgs = false, logReturn = false)
    @DetectCrawler(operation = "流式播放", checkReferer = false, checkBehavior = false)
    @GetMapping("/stream/{songId}")
    public void streamSong(
            @PathVariable("songId") Long songId,
            @RequestParam(defaultValue = "standard") String quality,
            @RequestParam(required = false) String grant,
            @RequestHeader(value = "Range", required = false) String range,
            @RequestAttribute(value = "userId", required = false) Long userId,
            HttpServletResponse response) {
        songService.streamSong(songId, quality, grant, range, userId, response);
    }

                                                        

       
           
       
    @ApiLog("点赞歌曲")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 3600, operation = "likeSong",
               message = "点赞操作过于频繁，请稍后再试")
    @PostMapping({"/like/{songId}", "/like/like/{songId}"})
    public Result<Boolean> likeSong(@PathVariable("songId") Long songId,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.likeSong(userId, songId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("取消点赞歌曲")
    @DeleteMapping({"/like/{songId}", "/like/like/{songId}"})
    public Result<Boolean> unlikeSong(@PathVariable("songId") Long songId,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.unlikeSong(userId, songId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("切换点赞状态")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 3600, operation = "toggleLikeSong",
               message = "点赞操作过于频繁，请稍后再试")
    @PostMapping({"/like/toggle/{songId}", "/like/like/toggle/{songId}"})
    public Result<Boolean> toggleLike(@PathVariable("songId") Long songId,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.toggleLike(userId, songId);
        return Result.success(result);
    }

       
           
       
    @ApiLog("收藏歌曲")
    @RateLimit(maxRequests = 120, timeWindowSeconds = 60, operation = "favoriteSong",
               message = "收藏操作过于频繁，请稍后再试")
    @PostMapping({"/favorite/{songId}", "/like/favorite/{songId}"})
    public Result<Boolean> favoriteSong(@PathVariable("songId") Long songId,
                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.favoriteSong(userId, songId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("取消收藏歌曲")
    @DeleteMapping({"/favorite/{songId}", "/like/favorite/{songId}"})
    public Result<Boolean> unfavoriteSong(@PathVariable("songId") Long songId,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.unfavoriteSong(userId, songId);
        return Result.success(result);
    }

       
             
       
    @ApiLog("切换收藏状态")
    @RateLimit(maxRequests = 120, timeWindowSeconds = 60, operation = "toggleFavoriteSong",
               message = "收藏操作过于频繁，请稍后再试")
    @PostMapping({"/favorite/toggle/{songId}", "/like/favorite/toggle/{songId}"})
    public Result<Boolean> toggleFavorite(@PathVariable("songId") Long songId,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Boolean result = songLikeService.toggleFavorite(userId, songId);
        return Result.success(result);
    }

       
                  
       
    @ApiLog("检查歌曲状态")
    @GetMapping({"/status/{songId}", "/like/status/{songId}"})
    public Result<Map<String, Boolean>> checkSongStatus(@PathVariable("songId") Long songId,
                                                       @RequestAttribute(value = "userId", required = false) Long userId) {
        Map<String, Boolean> status = new java.util.HashMap<>();
        status.put("isLiked", userId != null && songLikeService.isLiked(userId, songId));
        status.put("isFavorited", userId != null && songLikeService.isFavorited(userId, songId));
        return Result.success(status);
    }

       
               
       
    @ApiLog("批量获取歌曲状态")
    @PostMapping({"/status/batch", "/like/status/batch"})
    public Result<Map<Long, Map<String, Boolean>>> getBatchSongStatus(
            @RequestBody List<Long> songIds,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.success(new java.util.HashMap<>());
        }
        Map<Long, Map<String, Boolean>> result = songLikeService.getBatchSongStatus(userId, songIds);
        return Result.success(result);
    }

       
                  
       
    @ApiLog("获取收藏歌曲列表")
    @GetMapping({"/favorite/list", "/like/favorite/list"})
    public Result<IPage<SongLike>> getFavoriteSongs(PageQuery pageQuery,
                                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        IPage<SongLike> result = songLikeService.getFavoriteSongs(userId, pageQuery);
        return Result.success(result);
    }

       
                                                 
       
    @ApiLog("获取收藏歌曲详情列表")
    @GetMapping({"/favorite/details", "/like/favorite/details"})
    public Result<IPage<SongVO>> getFavoriteSongDetails(PageQuery pageQuery,
                                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        IPage<SongVO> result = songService.getFavoriteSongDetails(userId, pageQuery);
        return Result.success(result);
    }

       
               
       
    @ApiLog("获取收藏数量")
    @GetMapping({"/favorite/count", "/like/favorite/count"})
    public Result<Integer> getFavoriteCount(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.success(0);
        }
        Integer count = songLikeService.getFavoriteCount(userId);
        return Result.success(count);
    }

                                                     

       
                
       
    @ApiLog("歌曲评分")
    @PostMapping("/rating")
    public Result<SongRatingVO> rateSong(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @Valid @RequestBody SongRatingDTO dto) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        SongRatingVO result = songRatingService.rateSong(userId, dto);
        return Result.success(result);
    }

       
               
       
    @ApiLog("获取歌曲评分")
    @GetMapping("/rating/{songId}")
    public Result<SongRatingVO> getSongRating(
            @PathVariable("songId") Long songId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        SongRatingVO result = songRatingService.getSongRating(songId, userId);
        return Result.success(result);
    }

       
                 
       
    @ApiLog("删除歌曲评分")
    @DeleteMapping("/rating/{songId}")
    public Result<Void> deleteRating(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable("songId") Long songId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        songRatingService.deleteRating(userId, songId);
        return Result.success();
    }

       
                
       
    @ApiLog("获取用户评分列表")
    @GetMapping("/rating/user/list")
    public Result<Object> getUserRatings(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Object result = songRatingService.getUserRatings(userId);
        return Result.success(result);
    }

       
               
       
    @ApiLog("批量评分歌曲")
    @PostMapping("/rating/batch")
    public Result<Void> batchRateSong(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody Map<Long, Integer> ratings) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        songRatingService.batchRateSong(userId, ratings);
        return Result.success();
    }

       
                 
       
    @ApiLog("获取用户评分统计")
    @GetMapping("/rating/stats")
    public Result<Map<String, Object>> getUserRatingStats(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> stats = songRatingService.getUserRatingStats(userId);
        return Result.success(stats);
    }

                                                     

       
           
       
    @ApiLog("下载歌曲")
    @GetMapping("/download/{songId}")
    public void downloadSong(
            @PathVariable("songId") Long songId,
            @RequestParam(defaultValue = "standard") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId,
            HttpServletResponse response) {
        songDownloadService.downloadSong(songId, quality, userId, response);
    }

       
               
       
    @ApiLog("获取歌曲下载链接")
    @GetMapping("/download/{songId}/info")
    public Result<Object> getDownloadInfo(
            @PathVariable("songId") Long songId,
            @RequestParam(defaultValue = "standard") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        Object result = songDownloadService.getDownloadInfo(songId, quality, userId);
        return Result.success(result);
    }

                                                       

       
               
       
    @ApiLog("创建歌曲资源申请")
    @PostMapping("/resource-request")
    public Result<Long> createRequest(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid SongResourceRequestAddDTO dto) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Long requestId = songResourceRequestService.createRequest(userId, "", dto);
        return Result.success(requestId);
    }

       
                
       
    @ApiLog("上传文件并创建歌曲资源申请")
    @PostMapping("/resource-request/upload")
    public Result<Long> createRequestWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("songName") String songName,
            @RequestParam("artistName") String artistName,
            @RequestParam(value = "albumName", required = false) String albumName,
            @RequestParam(value = "versionInfo", required = false, defaultValue = "原版") String versionInfo,
            @RequestParam(value = "sourceDescription", required = false) String sourceDescription,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Long requestId = songResourceRequestService.createRequestWithFile(
                userId, "", songName, artistName, albumName, versionInfo, sourceDescription, remark, file
        );
        return Result.success(requestId);
    }

       
                 
       
    @GetMapping("/resource-request/check")
    public Result<Boolean> checkRequested(
            @RequestParam("songName") String songName,
            @RequestParam("artistName") String artistName,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        boolean hasRequested = songResourceRequestService.hasRequestedSong(userId, songName, artistName);
        return Result.success(hasRequested);
    }

       
               
       
    @GetMapping("/resource-request/today-count")
    public Result<Integer> getTodayCount(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        int count = songResourceRequestService.getTodayRequestCount(userId);
        return Result.success(count);
    }

       
                  
       
    @ApiLog("查询用户歌曲资源申请记录")
    @GetMapping("/resource-request/my")
    public Result<IPage<SongResourceRequestVO>> getMyRequests(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size,
            @RequestParam(value = "status", required = false) String status,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        IPage<SongResourceRequestVO> result = songResourceRequestService.getUserRequests(
                userId,
                new PageQuery(page, size),
                status
        );
        return Result.success(result);
    }

       
                    
       
    @ApiLog("查询所有歌曲资源申请")
    @GetMapping("/resource-request/all")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<IPage<SongResourceRequestVO>> getAllRequests(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size,
            @RequestParam(value = "status", required = false) String status) {
        IPage<SongResourceRequestVO> result = songResourceRequestService.getAllRequests(new PageQuery(page, size), status);
        return Result.success(result);
    }

       
                    
       
    @ApiLog("处理歌曲资源申请")
    @PostMapping("/resource-request/handle")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<String> handleRequest(
            @RequestBody @Valid SongResourceRequestHandleDTO dto,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        String handlerName = "管理员";
        songResourceRequestService.handleRequest(userId, handlerName, dto);
        return Result.success("处理成功");
    }

       
              
       
    @GetMapping("/resource-request/pending-count")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Long> getPendingCount() {
        long count = songResourceRequestService.getPendingCount();
        return Result.success(count);
    }
}
