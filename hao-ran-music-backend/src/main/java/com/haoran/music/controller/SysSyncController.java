package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.Comment;
import com.haoran.music.entity.MV;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.CommentMapper;
import com.haoran.music.mapper.MVMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

   
                      
                                  
   
@Slf4j
@RestController
@RequestMapping("/sys/sync")
@RequiredArgsConstructor
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class SysSyncController {

    private final CommentMapper commentMapper;
    private final MVMapper mvMapper;

       
                  
       
    @ApiLog("同步MV评论数")
    @PostMapping("/mv-comment/{mvId}")
    public Result<Map<String, Object>> syncMVCommentCount(@PathVariable Long mvId) {
        MV mv = mvMapper.selectById(mvId);
        if (mv == null) {
            return Result.error("MV不存在");
        }

                                            
        int actualCount = Math.toIntExact(commentMapper.selectCount(
            new LambdaQueryWrapper<Comment>()
                .eq(Comment::getTargetType, 4)
                .eq(Comment::getTargetId, mvId)
                .eq(Comment::getParentId, 0)           
                .eq(Comment::getStatus, CommonConstants.STATUS_NORMAL)            
                .eq(Comment::getDeleted, CommonConstants.NOT_DELETED)
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("mvId", mvId);
        result.put("mvName", mv.getName());
        result.put("oldCommentCount", mv.getCommentCount());
        result.put("newCommentCount", Long.valueOf(actualCount));

               
        if (mv.getCommentCount() == null || mv.getCommentCount() != actualCount) {
            mv.setCommentCount(Long.valueOf(actualCount));
            mvMapper.updateById(mv);
            result.put("updated", true);
            log.info("event=mv_comment_count_synced mvId={} previousCount={} currentCount={}",
                    mvId, mv.getCommentCount(), actualCount);
        } else {
            result.put("updated", false);
        }

        return Result.success(result);
    }
}
