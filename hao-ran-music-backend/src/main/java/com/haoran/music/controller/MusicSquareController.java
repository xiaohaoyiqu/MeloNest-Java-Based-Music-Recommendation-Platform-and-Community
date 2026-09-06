




package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ClientIpResolver;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;




@Slf4j
@RestController
@RequestMapping("/music-square")
public class MusicSquareController {

    @Autowired
    private MusicPostService musicPostService;

    @Autowired
    private MusicTopicService musicTopicService;

    @Autowired
    private HotEventService hotEventService;

    @Autowired
    private SongVoteService songVoteService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private UserFollowService userFollowService;

    @Autowired
    private PostImageService postImageService;

    @Autowired
    private VideoPostService videoPostService;

    @Autowired
    private MarketplaceService marketplaceService;






    @PostMapping("/posts/images/upload")
    @ApiLog("上传动态图片")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 3600, operation = "uploadPostImage",
               message = "图片上传过于频繁，请稍后再试")
    public Result<Map<String, String>> uploadPostImage(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        Map<String, String> result = postImageService.uploadPostImage(file, userId);
        if (result.containsKey("error")) {
            return Result.error(result.get("error"));
        }

        return Result.success(result);
    }




    @PostMapping("/posts/images/batch")
    @ApiLog("批量上传动态图片")
    @RateLimit(maxRequests = 10, timeWindowSeconds = 3600, operation = "uploadPostImagesBatch",
               message = "批量上传过于频繁，请稍后再试")
    public Result<List<Map<String, String>>> uploadPostImages(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        List<Map<String, String>> results = postImageService.uploadPostImages(files, userId);
        return Result.success(results);
    }




    @GetMapping("/posts/{postId}/images/{index}/original")
    @ApiLog("获取动态原图")
    public Result<String> getPostOriginalImage(
            @PathVariable Long postId,
            @PathVariable Integer index,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        String originalUrl = postImageService.getOriginalImageUrl(postId, index, userId);
        if (originalUrl == null) {
            return Result.error("原图不存在");
        }
        return Result.successData(originalUrl);
    }

    @GetMapping("/posts/{postId}/images/{index}/content")
    public void deliverPostImage(
            @PathVariable Long postId,
            @PathVariable Integer index,
            @RequestParam(defaultValue = "compressed") String variant,
            @RequestAttribute(value = "userId", required = false) Long userId,
            javax.servlet.http.HttpServletResponse response) {
        postImageService.deliverPostImage(postId, index, variant, userId, response);
    }






    @PostMapping("/posts/videos/upload")
    @ApiLog("上传视频动态")
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "uploadVideoPost",
               message = "视频上传过于频繁，请稍后再试")
    public Result<Map<String, Object>> uploadVideoPost(
            @RequestParam("file") MultipartFile file,
            @RequestParam("content") String content,
            @RequestParam(value = "topics", required = false) String topics,
            @RequestParam(value = "allowComment", defaultValue = "true") Boolean allowComment,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }

        Map<String, Object> result = videoPostService.uploadVideoPost(file, userId, content, topics, allowComment);
        if (!(Boolean) result.getOrDefault("success", false)) {
            return Result.error(result.getOrDefault("error", "上传失败").toString());
        }

        return Result.success(result);
    }




    @GetMapping("/posts/videos/{postId}/status")
    public Result<Map<String, Object>> getVideoStatus(
            @PathVariable Long postId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        return Result.success(videoPostService.getVideoPostStatus(postId, userId));
    }




    @GetMapping("/posts/videos/my")
    public Result<List<Map<String, Object>>> getMyVideoPosts(
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(videoPostService.getMyVideoPosts(userId, status));
    }




    @GetMapping("/posts/videos/stats")
    public Result<Map<String, Object>> getVideoPostStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(videoPostService.getVideoPostStats(userId));
    }




    @PostMapping("/posts/videos/{postId}/retry")
    public Result<Map<String, Object>> retryVideoProcessing(
            @PathVariable Long postId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Map<String, Object> result = videoPostService.retryVideoPostProcessing(postId, userId);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            return Result.error(result.getOrDefault("error", "重试失败").toString());
        }
        return Result.success(result);
    }




    @DeleteMapping("/posts/videos/{postId}")
    public Result<Boolean> deleteVideoPost(
            @PathVariable Long postId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(videoPostService.deleteVideoPost(postId, userId));
    }




    @GetMapping("/posts/videos/{postId}/play")
    public Result<String> getVideoPlayUrl(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "720p") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        String url = videoPostService.getVideoPlayUrl(postId, quality, userId);
        if (url == null) {
            return Result.error("视频不存在");
        }
        return Result.successData(url);
    }

    @GetMapping("/posts/videos/{postId}/content")
    public void deliverVideo(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "720p") String quality,
            @RequestAttribute(value = "userId", required = false) Long userId,
            javax.servlet.http.HttpServletResponse response) {
        videoPostService.deliverVideo(postId, quality, userId, response);
    }






    @GetMapping("/posts")
    public Result getPosts(
            @RequestParam(defaultValue = "all") String timeRange,
            @RequestParam(defaultValue = "recommend") String type,
            @RequestParam(required = false) Long topicId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        IPage<Object> result = musicPostService.getPosts(timeRange, type, topicId, userId, page, size);
        return Result.success(result);
    }




    @PostMapping("/posts")
    @ApiLog("发布动态")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "createPost",
               message = "动态发布过于频繁，请稍后再试")
    public Result createPost(HttpServletRequest request,
                            @RequestParam String content,
                            @RequestParam(required = false) String images,
                            @RequestParam(required = false) String resourceType,
                            @RequestParam(required = false) Long resourceId,
                            @RequestParam(required = false) String topics,
                            @RequestParam(defaultValue = "public") String visibility,
                            @RequestParam(defaultValue = "true") Boolean allowComment) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Long postId = musicPostService.createPost(userId, content, images, resourceType,
                resourceId, topics, visibility, allowComment);
        return Result.success(postId);
    }




    @PostMapping("/posts/diary")
    @ApiLog("发布听歌日记")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "createDiary",
               message = "听歌日记发布过于频繁，请稍后再试")
    public Result createListenDiary(HttpServletRequest request,
                                   @RequestParam String content,
                                   @RequestParam(required = false) String images,
                                   @RequestParam(required = false) String topics) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Long postId = musicPostService.createListenDiary(userId, content, images, topics);
        return Result.success(postId);
    }




    @PutMapping("/posts/{id}")
    @ApiLog("编辑动态")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "editPost",
               message = "编辑操作过于频繁，请稍后再试")
    public Result updatePost(@PathVariable Long id,
                           HttpServletRequest request,
                           @RequestParam String content) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = musicPostService.updatePost(id, userId, content);
        return Result.success(success);
    }




    @DeleteMapping("/posts/{id}")
    @ApiLog("删除动态")
    public Result deletePost(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = musicPostService.deletePost(id, userId);
        return Result.success(success);
    }




    @PostMapping("/posts/{id}/like")
    @ApiLog("点赞动态")
    public Result likePost(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = musicPostService.likePost(id, userId);
        return Result.success(success);
    }




    @DeleteMapping("/posts/{id}/like")
    @ApiLog("取消点赞")
    public Result unlikePost(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = musicPostService.unlikePost(id, userId);
        return Result.success(success);
    }




    @PatchMapping("/posts/{id}/comment-settings")
    @ApiLog("更新动态评论开关")
    public Result updatePostCommentSetting(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") Boolean allowComment,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(musicPostService.updatePostCommentSetting(id, userId, allowComment));
    }




    @PatchMapping("/posts/{id}/official-comment")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @ApiLog("官方更新动态评论开关")
    public Result updateOfficialPostCommentSetting(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") Boolean closed,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(musicPostService.updateOfficialCommentClosed(id, userId, closed));
    }






    @GetMapping("/topics/hot")
    public Result getHotTopics(@RequestParam(defaultValue = "10") Integer limit) {
        return Result.success(musicTopicService.getHotTopics(limit));
    }




    @GetMapping("/topics/personalized")
    @ApiLog("获取个性化推荐话题")
    public Result getPersonalizedTopics(
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(musicTopicService.getPersonalizedTopics(userId, limit));
    }




    @GetMapping("/topics/{id}")
    @ApiLog("获取话题详情")
    public Result getTopicDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(musicTopicService.getTopicDetail(id, userId));
    }




    @PostMapping("/topics/{id}/follow")
    @ApiLog("关注话题")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 3600, operation = "followTopic",
               message = "关注操作过于频繁，请稍后再试")
    public Result followTopic(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(musicTopicService.followTopic(id, userId));
    }




    @DeleteMapping("/topics/{id}/follow")
    @ApiLog("取消关注")
    public Result unfollowTopic(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(musicTopicService.unfollowTopic(id, userId));
    }






    @GetMapping("/events/featured")
    public Result getFeaturedEvents(@RequestParam(defaultValue = "5") Integer limit) {
        return Result.success(hotEventService.getFeaturedEvents(limit));
    }






    @GetMapping("/events/{id}")
    public Result getEventDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(hotEventService.getEventDetail(id, buildViewerKey(request)));
    }






    @PostMapping("/vote")
    @ApiLog("歌曲投票")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 3600, operation = "songVote",
               message = "投票过于频繁，请稍后再试")
    public Result voteSong(@RequestParam Long songId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(songVoteService.vote(songId, userId));
    }




    @DeleteMapping("/vote")
    @ApiLog("取消投票")
    public Result unvoteSong(@RequestParam Long songId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(songVoteService.unvote(songId, userId));
    }




    @GetMapping("/vote/hot")
    public Result getHotVotedSongs(
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(songVoteService.getHotVotedSongs(limit, userId));
    }




    @GetMapping("/vote/personalized")
    @ApiLog("获取个性化推荐投票歌曲")
    public Result getPersonalizedVotedSongs(
            @RequestParam(defaultValue = "10") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(songVoteService.getPersonalizedVotedSongs(userId, limit));
    }




    @GetMapping("/vote/stats")
    public Result getTodayVoteStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(songVoteService.getTodayVoteStats(userId));
    }






    @GetMapping("/recommend-users")
    public Result getRecommendUsers(
            @RequestParam(defaultValue = "5") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userFollowService.getRecommendUsers(userId, limit));
    }




    @GetMapping("/recommend-users/personalized")
    @ApiLog("获取个性化推荐用户")
    public Result getPersonalizedRecommendUsers(
            @RequestParam(defaultValue = "5") Integer limit,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userFollowService.getPersonalizedRecommendUsers(userId, limit));
    }






    @GetMapping("/marketplace")
    @ApiLog("获取交易商品列表")
    public Result getMarketplaceItems(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(defaultValue = "all") String condition,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(marketplaceService.getItems(category, condition, sortBy, keyword, userId, page, size));
    }




    @GetMapping("/marketplace/{id}")
    @ApiLog("获取商品详情")
    public Result getMarketplaceItemDetail(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(marketplaceService.getItemDetail(id, userId, buildViewerKey(request)));
    }




    @PostMapping("/marketplace")
    @ApiLog("发布交易商品")
    @RateLimit(maxRequests = 3, timeWindowSeconds = 3600, operation = "createMarketplaceItem",
               message = "商品发布过于频繁，请稍后再试")
    public Result createMarketplaceItem(
            HttpServletRequest request,
            @RequestParam String title,
            @RequestParam(defaultValue = "other") String category,
            @RequestParam(defaultValue = "good") String condition,
            @RequestParam java.math.BigDecimal price,
            @RequestParam(required = false) java.math.BigDecimal originalPrice,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String images,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) String resourceName,
            @RequestParam(required = false) String resourceCover,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "both") String deliveryMethod) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Long itemId = marketplaceService.createItem(userId, title, category, condition,
                price, originalPrice, description, images, resourceType,
                resourceId, resourceName, resourceCover, location, deliveryMethod);
        return Result.success(itemId);
    }




    @PutMapping("/marketplace/{id}/status")
    @ApiLog("更新商品状态")
    public Result updateMarketplaceItemStatus(
            @PathVariable Long id,
            @RequestParam String status,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = marketplaceService.updateItemStatus(id, userId, status);
        return Result.success(success);
    }




    @DeleteMapping("/marketplace/{id}")
    @ApiLog("删除商品")
    public Result deleteMarketplaceItem(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = marketplaceService.deleteItem(id, userId);
        return Result.success(success);
    }




    @PutMapping("/marketplace/{id}/edit")
    @ApiLog("编辑商品")
    @RateLimit(maxRequests = 5, timeWindowSeconds = 3600, operation = "editMarketplaceItem",
               message = "商品编辑过于频繁，请稍后再试")
    public Result updateMarketplaceItem(
            @PathVariable Long id,
            HttpServletRequest request,
            @RequestParam String title,
            @RequestParam(defaultValue = "other") String category,
            @RequestParam(defaultValue = "good") String condition,
            @RequestParam java.math.BigDecimal price,
            @RequestParam(required = false) java.math.BigDecimal originalPrice,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String images,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) String resourceName,
            @RequestParam(required = false) String resourceCover,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "both") String deliveryMethod) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = marketplaceService.updateItem(id, userId, title, category, condition,
                price, originalPrice, description, images, resourceType,
                resourceId, resourceName, resourceCover, location, deliveryMethod);
        return Result.success(success);
    }




    @PostMapping("/marketplace/{id}/favorite")
    @ApiLog("收藏商品")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 3600, operation = "favoriteItem",
               message = "收藏操作过于频繁，请稍后再试")
    public Result favoriteMarketplaceItem(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = marketplaceService.favoriteItem(id, userId);
        return Result.success(success);
    }




    @DeleteMapping("/marketplace/{id}/favorite")
    @ApiLog("取消收藏商品")
    public Result unfavoriteMarketplaceItem(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Boolean success = marketplaceService.unfavoriteItem(id, userId);
        return Result.success(success);
    }




    @GetMapping("/marketplace/my")
    @ApiLog("获取我的商品")
    public Result getMyMarketplaceItems(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(marketplaceService.getMyItems(userId, status, page, size));
    }




    @GetMapping("/marketplace/favorites")
    @ApiLog("获取收藏商品")
    public Result getMyFavoriteItems(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        return Result.success(marketplaceService.getMyFavorites(userId, page, size));
    }






    @GetMapping("/monitor/operation-stats")
    @ApiLog("获取操作统计")
    public Result<Map<String, Object>> getOperationStats(
            @RequestParam(defaultValue = "24") Integer hours,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Map<String, Object> stats = marketplaceService.getOperationStats(userId, hours);
        return Result.success(stats);
    }






    @GetMapping("/posts/user/{userId}")
    @ApiLog("获取用户动态列表")
    public Result getUserPosts(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        IPage<Object> result = musicPostService.getUserPosts(userId, currentUserId, page, size);
        return Result.success(result);
    }




    @GetMapping("/posts/my")
    @ApiLog("获取我的动态列表")
    public Result getMyPosts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        IPage<Object> result = musicPostService.getUserPosts(userId, userId, page, size);
        return Result.success(result);
    }




    @GetMapping("/posts/my/type")
    @ApiLog("按类型获取我的动态")
    public Result getMyPostsByType(
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        IPage<Object> result = musicPostService.getUserPostsByType(userId, type, userId, page, size);
        return Result.success(result);
    }




    @GetMapping("/posts/user/{userId}/stats")
    @ApiLog("获取用户动态统计")
    public Result getUserPostStats(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        Map<String, Object> stats = musicPostService.getUserPostStats(userId, currentUserId);
        return Result.success(stats);
    }




    @GetMapping("/posts/my/stats")
    @ApiLog("获取我的动态统计")
    public Result getMyPostStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Map<String, Object> stats = musicPostService.getUserPostStats(userId, userId);
        return Result.success(stats);
    }




    @GetMapping("/posts/{id}/detail")
    @ApiLog("获取动态详情")
    public Result getPostDetail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> detail = musicPostService.getPostDetail(id, userId);
        if (detail == null) {
            return Result.error(404, "动态不存在");
        }
        return Result.success(detail);
    }




    @DeleteMapping("/posts/batch")
    @ApiLog("批量删除动态")
    public Result batchDeletePosts(
            @RequestBody java.util.List<Long> postIds,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Integer count = musicPostService.batchDeletePosts(postIds, userId);
        return Result.success("成功删除" + count + "条动态");
    }

    private String buildViewerKey(HttpServletRequest request) {
        Object userId = request == null ? null : request.getAttribute("userId");
        if (userId != null) {
            return "u:" + userId;
        }
        String ip = request == null ? null : clientIpResolver.resolve(request);
        if (ip == null || ip.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("a:");
            for (int i = 0; i < 12; i++) {
                value.append(String.format("%02x", digest[i]));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
