   
                      
                                       
   

package com.haoran.music.controller;



import com.haoran.music.enums.UserRole;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.constant.ModerationConstants;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.Moderation;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.service.ModerationAssignmentService;
import com.haoran.music.service.ModerationRecordService;
import com.haoran.music.service.ModerationService;
import com.haoran.music.service.OnlineStatusService;
import com.haoran.music.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

   
          
                       
   
@Slf4j
@RestController
@RequestMapping("/moderation")
public class ModerationController {

    @Autowired
    private ModerationRecordService moderationRecordService;

    @Autowired
    private ModerationService moderationService;

    @Autowired
    private OnlineStatusService onlineStatusService;

    @Autowired
    private ModerationAssignmentService assignmentService;

    @Autowired
    private PermissionService permissionService;

       
               
      
                     
  
    @ApiLog("获取工作状态")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/work-status")
    public Result<Map<String, Object>> getWorkStatus() {
        Map<String, Object> status = new HashMap<>();
        List<Long> onlineModerators = onlineStatusService.getOnlineModerators();
        List<Long> activeModerators = onlineStatusService.getActiveModerators();
        status.put("isWorkTime", onlineStatusService.isWorkTime());
        status.put("onlineModerators", onlineModerators);
        status.put("activeModerators", activeModerators);
        status.put("onlineModeratorDetails", onlineStatusService.getOnlineModeratorDetails());
        status.put("activeModeratorDetails", onlineStatusService.getActiveModeratorDetails());
        status.put("onlineModeratorCount", onlineModerators.size());
        status.put("activeModeratorCount", activeModerators.size());
        status.put("moderatorLoads", assignmentService.getAllModeratorLoads());
        return Result.success(status);
    }

       
                
      
                      
  
    @ApiLog("获取在线审核员")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/online-moderators")
    public Result<List<Long>> getOnlineModerators() {
        List<Long> moderators = onlineStatusService.getOnlineModerators();
        return Result.success(moderators);
    }

       
                
      
                      
  
    @ApiLog("获取活跃审核员")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/active-moderators")
    public Result<List<Long>> getActiveModerators() {
        List<Long> moderators = onlineStatusService.getActiveModerators();
        return Result.success(moderators);
    }

       
                
      
                           
  
    @ApiLog("获取审核员负载")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/moderator-loads")
    public Result<Map<Long, Integer>> getModeratorLoads() {
        Map<Long, Integer> loads = assignmentService.getAllModeratorLoads();
        return Result.success(loads);
    }

       
                    
      
                            
                 
  
    @ApiLog("用户下线")
    @PostMapping("/offline")
    public Result<Void> offline(HttpServletRequest request) {
        Long userId = getUserIdFromRequest(request);
        if (userId != null) {
            onlineStatusService.userOffline(userId);
            log.info("用户下线: userId={}", userId);
        }
        return Result.success();
    }

       
             
      
                             
                           
                               
                                   
                                  
                     
  
    @ApiLog("创建审核记录")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/create")
    public Result<Long> createRecord(@RequestParam String targetType,
                                     @RequestParam Long targetId,
                                     @RequestParam Long submitterId,
                                     @RequestParam String submitterSource,
                                     @RequestParam(required = false, defaultValue = "5") Integer priority,
                                     HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        if (!permissionService.isAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "Generic moderation records can only be created by administrators");
        }
        Long recordId = moderationRecordService.createRecord(
                targetType, targetId, submitterId, submitterSource, priority);
        return Result.success(recordId);
    }

       
             
      
                             
                       
  
    @ApiLog("分配审核任务")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/assign/{recordId}")
    public Result<Long> assignModerator(@PathVariable Long recordId,
                                        @RequestParam(required = false) Long moderatorId,
                                        HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        ensureCanOperateRecord(currentUserId, recordId);
        Long targetModeratorId = moderatorId == null ? currentUserId : moderatorId;
        if (!currentUserId.equals(targetModeratorId) && !permissionService.isAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only administrators can assign tasks to coworkers");
        }
        Long assignedModeratorId = moderationRecordService.assignModerator(recordId, targetModeratorId);
        return Result.success(assignedModeratorId);
    }

       
           
      
                             
                            
                 
  
    @ApiLog("开始审核")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/start/{recordId}")
    public Result<Void> startReview(@PathVariable Long recordId, HttpServletRequest request) {
        Long reviewerId = getUserIdFromRequest(request);
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        ensureCanOperateRecord(reviewerId, recordId);
        moderationRecordService.startReview(recordId, reviewerId);
        return Result.success();
    }

       
           
      
                             
                             
                            
                 
  
    @ApiLog("通过审核")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/approve/{recordId}")
    public Result<Void> approve(@PathVariable Long recordId,
                                @RequestParam(required = false) String reason,
                                HttpServletRequest request) {
        Long reviewerId = getUserIdFromRequest(request);
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        ensureCanOperateRecord(reviewerId, recordId);
        moderationRecordService.completeReview(recordId, reviewerId, "approved", reason);
        return Result.success();
    }

       
           
      
                             
                         
                            
                 
  
    @ApiLog("拒绝审核")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/reject/{recordId}")
    public Result<Void> reject(@PathVariable Long recordId,
                               @RequestParam String reason,
                               HttpServletRequest request) {
        Long reviewerId = getUserIdFromRequest(request);
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        ensureCanOperateRecord(reviewerId, recordId);
        moderationRecordService.completeReview(recordId, reviewerId, "rejected", reason);
        return Result.success();
    }

       
           
      
                             
                         
                            
                 
  
    @ApiLog("跳过审核")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/skip/{recordId}")
    public Result<Void> skip(@PathVariable Long recordId,
                             @RequestParam String reason,
                             HttpServletRequest request) {
        Long reviewerId = getUserIdFromRequest(request);
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        ensureCanOperateRecord(reviewerId, recordId);
        moderationRecordService.skipReview(recordId, reviewerId, reason);
        return Result.success();
    }

       
                
      
                                 
                                       
                            
                   
  
    @ApiLog("获取待分配任务")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending-assignments")
    public Result<IPage<ModerationRecord>> getPendingAssignments(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String submitterSource,
            PageQuery pageQuery,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        boolean includeAdminOnly = permissionService.isAdmin(currentUserId);
        IPage<ModerationRecord> result = moderationRecordService.getPendingAssignments(
                pageQuery, targetType, submitterSource, includeAdminOnly);
        return Result.success(result);
    }

       
                 
      
                               
                           
                            
                   
  
    @ApiLog("获取审核员任务")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/my-tasks/{moderatorId}")
    public Result<IPage<ModerationRecord>> getModeratorTasks(
            @PathVariable Long moderatorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            PageQuery pageQuery,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        ensureSelfOrAdmin(currentUserId, moderatorId);
        IPage<ModerationRecord> result = moderationRecordService.getModeratorTasks(
                moderatorId, status, targetType, pageQuery);
        return Result.success(result);
    }

       
                
      
                               
                   
  
    @ApiLog("获取审核员统计")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/stats/{moderatorId}")
    public Result<Map<String, Object>> getModeratorStats(@PathVariable Long moderatorId,
                                                         HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        ensureSelfOrAdmin(currentUserId, moderatorId);
        Map<String, Object> stats = moderationRecordService.getModeratorStats(moderatorId);
        return Result.success(stats);
    }

       
               
      
                   
  
    @ApiLog("获取全局审核统计")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/stats/global")
    public Result<Map<String, Object>> getGlobalStats(HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        Map<String, Object> stats = moderationRecordService.getGlobalStats(permissionService.isAdmin(currentUserId));
        return Result.success(stats);
    }

       
                 
      
                               
                    
  
    @ApiLog("检查审核员配额")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/has-quota/{moderatorId}")
    public Result<Boolean> hasQuota(@PathVariable Long moderatorId,
                                    HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        ensureSelfOrAdmin(currentUserId, moderatorId);
        boolean hasQuota = assignmentService.hasQuota(moderatorId);
        return Result.success(hasQuota);
    }


    @ApiLog("获取复审列表")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/reaudit/list")
    public Result<IPage<Moderation>> getReauditList(PageQuery pageQuery) {
        IPage<Moderation> result = moderationService.getModerationList(pageQuery, 3, null);
        return Result.success(result);
    }

    @ApiLog("处理复审")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/reaudit/process")
    public Result<Void> processReaudit(@RequestBody ReauditProcessDTO dto,
                                       HttpServletRequest request) {
        Long reviewerId = getUserIdFromRequest(request);
        if (reviewerId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        String reviewerName = "user:" + reviewerId;
        if (Integer.valueOf(1).equals(dto.getStatus())) {
            moderationService.approve(dto.getModerationId(), reviewerId, reviewerName);
            return Result.success();
        }
        if (Integer.valueOf(2).equals(dto.getStatus())) {
            moderationService.reject(dto.getModerationId(), reviewerId, reviewerName, dto.getReason());
            return Result.success();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "Review status can only be approved or rejected");
    }

    public static class ReauditProcessDTO {
        private Long moderationId;
        private Integer status;
        private String reason;

        public Long getModerationId() { return moderationId; }
        public void setModerationId(Long moderationId) { this.moderationId = moderationId; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
       
                 
      
                            
                   
  

    private void ensureCanOperateRecord(Long currentUserId, Long recordId) {
        ModerationRecord record = moderationRecordService.getById(recordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Moderation record does not exist");
        }
        if (ModerationConstants.isAdminOnlyTargetType(record.getTargetType()) && !permissionService.isAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only administrators can handle this moderation type");
        }
    }
    private void ensureSelfOrAdmin(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Moderator id is required");
        }
        if (!currentUserId.equals(targetUserId) && !permissionService.isAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only administrators can view coworker moderation data");
        }
    }
    private Long getUserIdFromRequest(HttpServletRequest request) {
        Long attrUserId = parseUserId(request.getAttribute("userId"));
        if (attrUserId != null) {
            return attrUserId;
        }
        return null;
    }

    private Long parseUserId(Object userId) {
        if (userId == null) {
            return null;
        }
        if (userId instanceof Long) {
            return (Long) userId;
        }
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        try {
            String value = String.valueOf(userId).trim();
            return value.isEmpty() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
