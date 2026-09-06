package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.dto.NotificationDetailVO;
import com.haoran.music.entity.Notification;
import com.haoran.music.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
                      
                     
   
@Slf4j
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

       
                 
      
                                    
                   
       
    @GetMapping("/unread-count")
    @ApiLog("获取未读通知数量")
    public Result<Integer> getUnreadCount(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        Long count = notificationService.getUnreadCount(userId);
        return Result.success(count.intValue());
    }

       
               
      
                                    
                       
                         
                   
       
    @GetMapping("/list")
    @ApiLog("获取通知列表")
    public Result<Page<Notification>> getNotificationList(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }

        int safePage = Math.max(page == null ? 1 : page, 1);
        int safeSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        Page<Notification> pageParam = new Page<>(safePage, safeSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);

        if (type != null && !type.trim().isEmpty()) {
            wrapper.eq(Notification::getType, type.trim());
        }
        if (isRead != null) {
            wrapper.eq(Notification::getIsRead, isRead ? 1 : 0);
        }

        Page<Notification> result = notificationService.page(pageParam, wrapper);
        return Result.success(result);
    }

       
             
      
                                    
                         
                   
       
    @GetMapping("/latest")
    @ApiLog("获取最新通知")
    public Result<List<Notification>> getLatestNotifications(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestParam(defaultValue = "10") Integer limit) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        int safeLimit = Math.min(Math.max(limit == null ? 10 : limit, 1), 50);
        List<Notification> notifications = notificationService.getUserNotifications(userId, safeLimit);
        return Result.success(notifications);
    }

       
                  
      
                           
                                  
                         
       
    @GetMapping("/group/{groupId}")
    @ApiLog("获取聚合通知详情")
    public Result<List<NotificationDetailVO>> getNotificationGroup(
            @PathVariable String groupId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }

                           
        if (!validateGroupOwnership(groupId, userId)) {
            return Result.error(403, "无权访问此通知");
        }

        List<NotificationDetailVO> details = notificationService.getNotificationGroupDetails(groupId, userId);
        return Result.success(details);
    }

       
              
      
                                 
                                             
                   
       
    @PostMapping("/read/{notificationId}")
    @ApiLog("标记通知已读")
    public Result<Void> markAsRead(
            @PathVariable Long notificationId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        boolean success = notificationService.markAsRead(notificationId, userId);
        return success ? Result.success() : Result.error(400, "操作失败");
    }

       
                
      
                                    
                    
       
    @PostMapping("/read-all")
    @ApiLog("标记所有通知已读")
    public Result<Integer> markAllAsRead(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        int count = notificationService.markAllAsRead(userId);
        return Result.success(count);
    }

       
           
      
                                 
                                             
                   
       
    @DeleteMapping("/{notificationId}")
    @ApiLog("删除通知")
    public Result<Void> deleteNotification(
            @PathVariable Long notificationId,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        boolean success = notificationService.deleteNotification(notificationId, userId);
        return success ? Result.success() : Result.error(400, "删除失败");
    }

       
                     
      
                              
                   
       
    @DeleteMapping("/delete-read")
    @ApiLog("删除全部已读通知")
    public Result<Integer> deleteReadNotifications(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(notificationService.deleteReadNotifications(userId));
    }

       
                   
      
                           
                          
                     
       
    private boolean validateGroupOwnership(String groupId, Long userId) {
        if (groupId == null || groupId.isEmpty()) {
            return false;
        }

                                                
        String[] parts = groupId.split(":");
        if (parts.length >= 2) {
            try {
                Long groupUserId = Long.parseLong(parts[1]);
                return groupUserId.equals(userId);
            } catch (NumberFormatException e) {
                log.warn("解析groupId失败: {}", groupId);
                return false;
            }
        }

        return false;
    }
}
