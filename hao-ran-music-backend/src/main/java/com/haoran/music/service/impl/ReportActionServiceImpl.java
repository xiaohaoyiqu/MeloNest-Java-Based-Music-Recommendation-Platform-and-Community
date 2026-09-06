   
                      
                          
   

package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.AdminAccountOperationGuard;
import com.haoran.music.entity.Report;
import com.haoran.music.entity.User;
import com.haoran.music.mapper.*;
import com.haoran.music.service.ReportActionService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.SimpleUserClassificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

   
             
   
@Slf4j
@Service
public class ReportActionServiceImpl implements ReportActionService {

    private final ReportMapper reportMapper;
    private final SongMapper songMapper;
    private final MVMapper mvMapper;
    private final AlbumMapper albumMapper;
    private final PlaylistMapper playlistMapper;
    private final CommentMapper commentMapper;
    private final MusicPostMapper musicPostMapper;
    private final MarketplaceItemMapper marketplaceItemMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Autowired(required = false)
    private SimpleUserClassificationService userClassificationService;

    public ReportActionServiceImpl(ReportMapper reportMapper,
                                   SongMapper songMapper,
                                   MVMapper mvMapper,
                                   AlbumMapper albumMapper,
                                   PlaylistMapper playlistMapper,
                                   CommentMapper commentMapper,
                                   MusicPostMapper musicPostMapper,
                                   MarketplaceItemMapper marketplaceItemMapper,
                                   UserMapper userMapper,
                                   NotificationService notificationService) {
        this.reportMapper = reportMapper;
        this.songMapper = songMapper;
        this.mvMapper = mvMapper;
        this.albumMapper = albumMapper;
        this.playlistMapper = playlistMapper;
        this.commentMapper = commentMapper;
        this.musicPostMapper = musicPostMapper;
        this.marketplaceItemMapper = marketplaceItemMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeAction(Long reportId, String action, Long reviewerId) {
                 
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("举报记录不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("reportId", reportId);
        result.put("action", action);

        Boolean success = false;
        String message = "";               

        switch (action) {
            case "warning":
                       
                Long warningUserId = getUserIdByType(report.getTargetType(), report.getTargetId());
                if (warningUserId != null) {
                    requireCanOperateTargetUser(reviewerId, warningUserId, "发送举报警告");
                    success = sendWarning(warningUserId, reportId, "您的内容被举报，请遵守社区规范");
                    message = success ? "警告已发送" : "警告发送失败";
                } else {
                    message = "无法获取用户信息";
                }
                break;

            case "hidden":
                       
                success = hideContentByType(report.getTargetType(), report.getTargetId(), "举报处理：隐藏内容");
                message = success ? "内容已隐藏" : "隐藏失败";
                break;

            case "deleted":
                       
                success = deleteContentByType(report.getTargetType(), report.getTargetId(), "举报处理：删除内容");
                message = success ? "内容已删除" : "删除失败";
                break;

            case "banned":
                       
                Long targetUserId = getUserIdByType(report.getTargetType(), report.getTargetId());
                if (targetUserId != null) {
                    success = banUser(targetUserId, "违反社区规范", 7, reviewerId);
                    message = success ? "用户已封禁7天" : "封禁失败";
                } else {
                    message = "无法获取用户信息";
                }
                break;

            case "none":
                      
                success = true;
                message = "无需处理";
                break;

            default:
                message = "未知的处理措施";
                success = false;
        }

        result.put("success", success);
        result.put("message", message);

        log.info("执行举报处理: reportId={}, action={}, success={}", reportId, action, success);
        return result;
    }

    @Override
    public Boolean hideSong(Long songId, String reason) {
        UpdateWrapper<com.haoran.music.entity.Song> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", songId)
                .set("is_hidden", 1)
                .set("hidden_reason", reason)
                .set("hidden_time", LocalDateTime.now());
        return songMapper.update(null, wrapper) > 0;
    }

    @Override
    public Boolean hideMV(Long mvId, String reason) {
        UpdateWrapper<com.haoran.music.entity.MV> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", mvId)
                .set("is_hidden", 1)
                .set("hidden_reason", reason)
                .set("hidden_time", LocalDateTime.now());
        return mvMapper.update(null, wrapper) > 0;
    }

    @Override
    public Boolean hideAlbum(Long albumId, String reason) {
        UpdateWrapper<com.haoran.music.entity.Album> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", albumId)
                .set("is_hidden", 1)
                .set("hidden_reason", reason)
                .set("hidden_time", LocalDateTime.now());
        return albumMapper.update(null, wrapper) > 0;
    }

    @Override
    public Boolean hidePlaylist(Long playlistId, String reason) {
        UpdateWrapper<com.haoran.music.entity.Playlist> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", playlistId)
                .set("is_hidden", 1)
                .set("hidden_reason", reason)
                .set("hidden_time", LocalDateTime.now());
        return playlistMapper.update(null, wrapper) > 0;
    }

    @Override
    public Boolean deleteComment(Long commentId, String reason) {
        return commentMapper.deleteById(commentId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean banUser(Long userId, String reason, Integer banDays) {
        return banUser(userId, reason, banDays, UserContext.getCurrentUserId());
    }

    private Boolean banUser(Long userId, String reason, Integer banDays, Long operatorId) {
        User target = userMapper.selectById(userId);
        if (target == null) {
            return false;
        }
        AdminAccountOperationGuard.requireCanOperateAccount(
                getOperator(operatorId), target, "封禁用户");

        UpdateWrapper<com.haoran.music.entity.User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId)
                .set("status", 2)
                .set("is_banned", 1)
                .set("ban_reason", reason)
                .set("ban_start_time", LocalDateTime.now());

        if (banDays == null || banDays == 0) {
                   
            wrapper.set("ban_end_time", (Object) null);
        } else {
            wrapper.set("ban_end_time", LocalDateTime.now().plusDays(banDays));
        }

        boolean success = userMapper.update(null, wrapper) > 0;

        if (success) {
            if (userClassificationService != null) {
                userClassificationService.forceLogout(userId);
            }
                     
            notificationService.sendSystemNotification(userId, "账户封禁通知",
                    "您的账户因违反社区规范已被封禁" + (banDays == null || banDays == 0 ? "。" : banDays + "天。"), null);
        }

        return success;
    }

    private void requireCanOperateTargetUser(Long operatorId, Long targetUserId, String action) {
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        AdminAccountOperationGuard.requireCanOperateAccount(getOperator(operatorId), target, action);
    }

    private User getOperator(Long operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "当前操作账号不存在");
        }
        return operator;
    }

    @Override
    public Boolean sendWarning(Long userId, Long reportId, String warningContent) {
        try {
            notificationService.sendSystemNotification(userId, "内容警告", warningContent, null);
            return true;
        } catch (Exception e) {
            log.error("发送警告通知失败: userId={}, reportId={}", userId, reportId);
            return false;
        }
    }

    @Override
    public Boolean restoreContent(String targetType, Long targetId) {
        switch (targetType) {
            case "song":
                UpdateWrapper<com.haoran.music.entity.Song> wrapper1 = new UpdateWrapper<>();
                wrapper1.eq("id", targetId)
                        .set("is_hidden", 0)
                        .set("hidden_reason", (Object) null)
                        .set("hidden_time", (Object) null);
                return songMapper.update(null, wrapper1) > 0;
            case "mv":
                UpdateWrapper<com.haoran.music.entity.MV> wrapper2 = new UpdateWrapper<>();
                wrapper2.eq("id", targetId)
                        .set("is_hidden", 0)
                        .set("hidden_reason", (Object) null)
                        .set("hidden_time", (Object) null);
                return mvMapper.update(null, wrapper2) > 0;
            case "album":
                UpdateWrapper<com.haoran.music.entity.Album> wrapper3 = new UpdateWrapper<>();
                wrapper3.eq("id", targetId)
                        .set("is_hidden", 0)
                        .set("hidden_reason", (Object) null)
                        .set("hidden_time", (Object) null);
                return albumMapper.update(null, wrapper3) > 0;
            case "playlist":
                UpdateWrapper<com.haoran.music.entity.Playlist> wrapper4 = new UpdateWrapper<>();
                wrapper4.eq("id", targetId)
                        .set("is_hidden", 0)
                        .set("hidden_reason", (Object) null)
                        .set("hidden_time", (Object) null);
                return playlistMapper.update(null, wrapper4) > 0;
            case "post":
                return musicPostMapper.update(null, new UpdateWrapper<com.haoran.music.entity.MusicPost>()
                        .eq("id", targetId)
                        .eq("is_deleted", 0)
                        .set("visibility", "private")
                        .set("official_comment_closed", 1)
                        .set("update_time", LocalDateTime.now())) > 0;
            case "marketplace_item":
            case "MARKETPLACE_ITEM":
                return marketplaceItemMapper.update(null, new UpdateWrapper<com.haoran.music.entity.MarketplaceItem>()
                        .eq("id", targetId)
                        .eq("is_deleted", 0)
                        .set("status", "removed")) > 0;
            default:
                return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchExecuteAction(Long[] reportIds, String action, Long reviewerId) {
        int count = 0;
        for (Long reportId : reportIds) {
            try {
                Map<String, Object> result = executeAction(reportId, action, reviewerId);
                if ((Boolean) result.get("success")) {
                    count++;
                }
            } catch (Exception e) {
                log.error("批量处理举报失败: reportId={}, action={}", reportId, action);
            }
        }
        return count;
    }

    @Override
    public Boolean validateReportForAction(Long reportId) {
                                          
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            log.warn("举报记录不存在: reportId={}", reportId);
            return false;
        }
                                
        if ("pending".equals(report.getStatus())) {
            return true;
        }
        log.warn("举报已处理，无法重复处理: reportId={}, status={}", reportId, report.getStatus());
        return false;
    }

       
               
       
    private Boolean hideContentByType(String targetType, Long targetId, String reason) {
        switch (targetType) {
            case "song":
                return hideSong(targetId, reason);
            case "mv":
                return hideMV(targetId, reason);
            case "album":
                return hideAlbum(targetId, reason);
            case "playlist":
                return hidePlaylist(targetId, reason);
            default:
                return false;
        }
    }

       
               
       
    private Boolean deleteContentByType(String targetType, Long targetId, String reason) {
        switch (targetType) {
            case "comment":
                return deleteComment(targetId, reason);
            case "song":
                        
                return hideSong(targetId, reason);
            case "mv":
                        
                return hideMV(targetId, reason);
            case "album":
                return hideAlbum(targetId, reason);
            case "playlist":
                return hidePlaylist(targetId, reason);
            case "post":
                return musicPostMapper.update(null, new UpdateWrapper<com.haoran.music.entity.MusicPost>()
                        .eq("id", targetId)
                        .eq("is_deleted", 0)
                        .set("is_deleted", 1)
                        .set("update_time", LocalDateTime.now())) > 0;
            case "marketplace_item":
            case "MARKETPLACE_ITEM":
                return marketplaceItemMapper.update(null, new UpdateWrapper<com.haoran.music.entity.MarketplaceItem>()
                        .eq("id", targetId)
                        .eq("is_deleted", 0)
                        .set("status", "removed")) > 0;
            default:
                return false;
        }
    }

       
                 
      
                                                                   
                           
                                 
       
    private Long getUserIdByType(String targetType, Long targetId) {
        if (targetId == null) {
            return null;
        }

        switch (targetType) {
            case "song":
                com.haoran.music.entity.Song song = songMapper.selectById(targetId);
                return song != null ? song.getUploaderId() : null;

            case "mv":
            case "album":
                                                                  
                return null;

            case "playlist":
                com.haoran.music.entity.Playlist playlist = playlistMapper.selectById(targetId);
                return playlist != null ? playlist.getUserId() : null;

            case "comment":
                com.haoran.music.entity.Comment comment = commentMapper.selectById(targetId);
                return comment != null ? comment.getUserId() : null;

            case "post":
                com.haoran.music.entity.MusicPost post = musicPostMapper.selectById(targetId);
                return post != null ? post.getUserId() : null;

            case "marketplace_item":
            case "MARKETPLACE_ITEM":
                com.haoran.music.entity.MarketplaceItem item = marketplaceItemMapper.selectById(targetId);
                return item != null ? item.getSellerId() : null;

            case "user":
                                   
                return targetId;

            default:
                log.warn("未知的举报目标类型: {}", targetType);
                return null;
        }
    }
}
