   
                      
                                   
   

package com.haoran.music.controller;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.entity.Notification;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.NotificationMapper;
import com.haoran.music.service.NotificationBroadcastTaskService;
import com.haoran.music.service.NotificationService;
import com.haoran.music.service.NotificationDeliveryOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
           
                
   
@Slf4j
@RestController
@RequestMapping("/admin/notification")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminNotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private NotificationBroadcastTaskService notificationBroadcastTaskService;

    @Resource
    private NotificationMapper notificationMapper;

    @Resource
    private NotificationDeliveryOutboxService notificationDeliveryOutboxService;

       
                   
      
                            
                                
                                
                   
       
    @PostMapping("/list")
    @ApiLog("管理员获取通知列表")
    public Result<IPage<Notification>> getNotificationList(
            @RequestBody PageQuery pageQuery,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long userId) {

        Page<Notification> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();

        if (type != null && !type.isEmpty()) {
            wrapper.eq(Notification::getType, type);
        }
        if (userId != null) {
            wrapper.eq(Notification::getUserId, userId);
        }

        wrapper.orderByDesc(Notification::getCreateTime);

        IPage<Notification> result = notificationService.page(page, wrapper);
        return Result.success(result);
    }

       
                    
      
                                 
                                 
                                   
                   
       
    @DeleteMapping("/recall/{notificationId}")
    @ApiLog("管理员撤回通知")
    public Result<Void> recallNotification(
            @PathVariable Long notificationId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }

        boolean success = notificationService.recallNotificationByAdmin(notificationId, adminId, reason);

        if (success) {
            return Result.success("撤回成功");
        }
        return Result.error("撤回失败，通知可能不存在");
    }

       
                
      
                         
                             
                            
                   
       
    @DeleteMapping("/recall/user/{userId}")
    @ApiLog("管理员批量撤回用户通知")
    public Result<Integer> recallUserNotifications(
            @PathVariable Long userId,
            @RequestParam(required = false) String type,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }

        int count = notificationService.batchRecallUserNotifications(userId, type);

        return Result.success("成功撤回 " + count + " 条通知", count);
    }

       
                
      
                         
                         
                            
                   
       
    @RequireRole({UserRole.SUPER_ADMIN})
    @DeleteMapping("/recall/type/{type}")
    @ApiLog("管理员按类型批量撤回通知")
    public Result<Integer> recallByType(
            @PathVariable String type,
            @RequestParam(required = false) String reason,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }

        int count = notificationService.batchRecallByType(type, reason);

        return Result.success("成功撤回 " + count + " 条通知", count);
    }

       
               
      
                      
       
    @GetMapping("/stats")
    @ApiLog("管理员获取通知统计")
    public Result<Map<String, Object>> getNotificationStats(HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }

        List<Map<String, Object>> typeStats = notificationMapper.selectAdminTypeStats();
        Map<String, Object> totals = notificationMapper.selectAdminTotals();
        Map<String, Object> stats = totals == null ? new HashMap<>() : new HashMap<>(totals);
        typeStats.forEach(row -> {
            Object type = row.get("type");
            if (type != null) {
                stats.put(String.valueOf(type), row.get("totalCount"));
            }
        });
        stats.put("types", typeStats);

        return Result.success(stats);
    }

       
                                
      
                               
       
    @GetMapping("/delivery-outbox/status")
    @ApiLog("管理员获取通知投递状态")
    public Result<Map<String, Object>> getDeliveryOutboxStatus() {
        return Result.success(notificationDeliveryOutboxService.getStatusSummary());
    }

       
                       
      
                         
                     
       
    @PostMapping("/delivery-outbox/retry")
    @ApiLog("管理员重试通知投递")
    public Result<Integer> retryDeliveryOutbox(@RequestParam(defaultValue = "20") Integer limit) {
        int safeLimit = ObjectUtils.isEmpty(limit) ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.success(notificationDeliveryOutboxService.retryDueEvents(safeLimit));
    }

       
                               
      
                        
                     
       
    @GetMapping("/delivery-outbox/failures")
    @ApiLog("管理员获取通知投递失败事件")
    public Result<List<Map<String, Object>>> getDeliveryOutboxFailures(
            @RequestParam(defaultValue = "20") Integer limit) {
        int safeLimit = ObjectUtils.isEmpty(limit) ? 20 : Math.max(1, Math.min(limit, 100));
        return Result.success(notificationDeliveryOutboxService.getRecentFailures(safeLimit));
    }

       
                              
      
                          
                           
       
    @PostMapping("/delivery-outbox/{eventId}/retry")
    @ApiLog("管理员重试单个通知投递事件")
    public Result<Boolean> retryFailedDeliveryEvent(@PathVariable String eventId) {
        boolean delivered = notificationDeliveryOutboxService.retryFailedEvent(eventId);
        return delivered ? Result.success(true) : Result.error("事件不存在、状态不可重试或会话仍不可用");
    }

       
                   
      
                                                          
                            
                   
       
    @PostMapping("/broadcast")
    @ApiLog("管理员发送系统公告")
    public Result<Map<String, Object>> sendBroadcast(
            @RequestBody Map<String, String> params,
            HttpServletRequest request) {

        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }

        String title = params.get("title");
        String content = params.get("content");
        String link = params.get("link");
        String coverUrl = params.get("coverUrl");

        if (title == null || title.isEmpty()) {
            return Result.error(400, "标题不能为空");
        }
        if (content == null || content.isEmpty()) {
            return Result.error(400, "内容不能为空");
        }

        try {
            Map<String, Object> result = notificationBroadcastTaskService.submit(
                    title, content, link, coverUrl, adminId);
            return Result.success("已提交后台发送任务", result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

       
                    
      
                     
       
    @GetMapping("/broadcast/tasks/status")
    @ApiLog("管理员获取广播任务状态汇总")
    public Result<Map<String, Object>> getBroadcastTaskStatus() {
        return Result.success(notificationBroadcastTaskService.getTaskStatus());
    }

       
                  
      
                         
                     
       
    @GetMapping("/broadcast/tasks/{taskId}")
    @ApiLog("管理员获取广播任务详情")
    public Result<Map<String, Object>> getBroadcastTask(@PathVariable String taskId) {
        Map<String, Object> task = notificationBroadcastTaskService.getTask(taskId);
        if (task == null) {
            return Result.error("广播任务不存在");
        }
        return Result.success(task);
    }

       
                   
      
                         
                     
       
    @PostMapping("/broadcast/tasks/{taskId}/retry")
    @ApiLog("管理员重试广播任务")
    public Result<Map<String, Object>> retryBroadcastTask(@PathVariable String taskId,
                                                          HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        if (adminId == null) {
            return Result.error(401, "请先登录");
        }
        try {
            Map<String, Object> task = notificationBroadcastTaskService.retry(taskId, adminId);
            if (task == null) {
                return Result.error("广播任务不存在");
            }
            return Result.success("已提交重试", task);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }
}
