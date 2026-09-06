package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.comment.CommentCreateDTO;
import com.haoran.music.service.CommentService;
import com.haoran.music.vo.comment.CommentVO;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;






@RestController
@RequestMapping("/comment")
public class CommentController {

    @Resource
    private CommentService commentService;







    @ApiLog("获取评论详情")

    @GetMapping("/info/{id}")
    public Result<CommentVO> getCommentById(@PathVariable("id") Long id,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        CommentVO result = commentService.getCommentById(id, userId);
        return Result.success(result);
    }









    @ApiLog("查询评论列表")

    @GetMapping("/page")
    public Result<IPage<CommentVO>> pageComments( @RequestParam Integer targetType, @RequestParam Long targetId,
            PageQuery pageQuery,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<CommentVO> result = commentService.pageComments(targetType, targetId, pageQuery, userId);
        return Result.success(result);
    }








    @ApiLog("获取子评论")

    @GetMapping("/{id}/replies")
    public Result<IPage<CommentVO>> getReplies(
            @PathVariable("id") Long id,
            PageQuery pageQuery,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        IPage<CommentVO> result = commentService.getReplies(id, pageQuery, userId);
        return Result.success(result);
    }







    @ApiLog("发表评论")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 3600, operation = "createComment",
               message = "评论发表过于频繁，请稍后再试")
    @PostMapping
    public Result<Long> createComment(@RequestBody @Valid CommentCreateDTO dto,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        Long result = commentService.createComment(userId, dto);
        return Result.success(result);
    }







    @ApiLog("删除评论")

    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(@PathVariable("id") Long id,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        commentService.deleteComment(userId, id);
        return Result.success();
    }







    @ApiLog("点赞评论")

    @PostMapping("/{id}/like")
    public Result<Void> likeComment(@PathVariable("id") Long id,
                                 @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        commentService.likeComment(userId, id);
        return Result.success();
    }







    @ApiLog("取消点赞评论")

    @DeleteMapping("/{id}/like")
    public Result<Void> unlikeComment(@PathVariable("id") Long id,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        commentService.unlikeComment(userId, id);
        return Result.success();
    }








    @ApiLog("获取热门评论")

    @GetMapping("/hot")
    public Result<List<CommentVO>> getHotComments( @RequestParam Integer targetType, @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<CommentVO> result = commentService.getHotComments(targetType, limit, userId);
        return Result.success(result);
    }







    @ApiLog("获取用户评论")

    @GetMapping("/my")
    public Result<IPage<CommentVO>> getUserComments(PageQuery pageQuery,
                                                @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        IPage<CommentVO> result = commentService.getUserComments(userId, pageQuery);
        return Result.success(result);
    }







    @ApiLog("编辑评论")
    @PutMapping("/edit")
    public Result<Void> editComment(@RequestBody @Valid com.haoran.music.dto.comment.CommentEditDTO dto,
                                    @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        commentService.editComment(userId, dto.getId(), dto.getContent());
        return Result.success();
    }







    @ApiLog("获取评论编辑历史")

    @GetMapping("/{id}/history")
    public Result<List<com.haoran.music.dto.comment.CommentEditHistoryVO>> getCommentEditHistory(
            @PathVariable("id") Long commentId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<com.haoran.music.dto.comment.CommentEditHistoryVO> result =
                commentService.getCommentEditHistory(commentId, userId);
        return Result.success(result);
    }









    @ApiLog("举报评论")
    @PostMapping("/report")
    public Result<Void> reportComment(@RequestParam Long commentId,
                                     @RequestParam String reason,
                                     @RequestParam(required = false) String description,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        commentService.reportComment(userId, commentId, reason, description);
        return Result.success();
    }











    @ApiLog("获取优质评论")
    @GetMapping("/quality/{targetType}/{targetId}")
    public Result<List<CommentVO>> getQualityComments(
            @PathVariable Integer targetType,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "20") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<CommentVO> result = commentService.getQualityComments(targetType, targetId, limit, userId);
        return Result.success(result);
    }









    @ApiLog("获取好友评论")
    @GetMapping("/friends/{targetType}/{targetId}")
    public Result<List<CommentVO>> getFriendComments(
            @PathVariable Integer targetType,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "客官请先登录");
        }
        List<CommentVO> result = commentService.getFriendComments(targetType, targetId, limit, userId);
        return Result.success(result);
    }









    @ApiLog("获取评论区推荐用户")
    @GetMapping("/users/{targetType}/{targetId}")
    public Result<List<com.haoran.music.vo.user.UserVO>> getCommentRecommendedUsers(
            @PathVariable Integer targetType,
            @PathVariable Long targetId,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        List<com.haoran.music.vo.user.UserVO> result =
                commentService.getCommentRecommendedUsers(targetType, targetId, limit, userId);
        return Result.success(result);
    }
}
