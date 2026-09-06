   
                      
   
package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.entity.AuditLog;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.AuditLogService;
import com.haoran.music.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/audit-log")
@RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final PermissionService permissionService;

    public AuditLogController(AuditLogService auditLogService,
                              PermissionService permissionService) {
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
    }

    @GetMapping("/record/{recordId}")
    @ApiLog("Get audit logs by record")
    public Result<List<AuditLog>> getRecordLogs(@PathVariable Long recordId) {
        return Result.success(auditLogService.getRecordLogs(recordId));
    }

    @GetMapping("/operator/{operatorId}")
    @ApiLog("Get audit logs by operator")
    public Result<IPage<AuditLog>> getOperatorLogs(
            @PathVariable Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        Long currentUserId = getUserIdFromRequest(request);
        ensureSelfOrAdmin(currentUserId, operatorId);
        PageQuery pageQuery = new PageQuery(page, size);
        return Result.success(auditLogService.getOperatorLogs(operatorId, startTime, endTime, pageQuery));
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/statistics")
    @ApiLog("Get audit statistics")
    public Result<Map<String, Object>> getAuditStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime[] range = defaultRange(startTime, endTime, 7);
        return Result.success(auditLogService.getAuditStatistics(range[0], range[1]));
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/moderator-work-stats")
    @ApiLog("Get moderator audit work stats")
    public Result<List<Map<String, Object>>> getModeratorWorkStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime[] range = defaultRange(startTime, endTime, 7);
        return Result.success(auditLogService.getModeratorWorkStats(range[0], range[1]));
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/type-stats")
    @ApiLog("Get audit type stats")
    public Result<Map<String, Object>> getAuditTypeStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        LocalDateTime[] range = defaultRange(startTime, endTime, 30);
        return Result.success(auditLogService.getAuditTypeStats(range[0], range[1]));
    }

    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/log")
    @ApiLog("Write audit log manually")
    public Result<Void> manualLog(@RequestBody Map<String, Object> requestBody,
                                  HttpServletRequest request) {
        Long operatorId = getUserIdFromRequest(request);
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }

        Long recordId = getLongValue(requestBody, "recordId");
        String action = getStringValue(requestBody, "action");
        String comment = getStringValue(requestBody, "comment");
        auditLogService.logAction(recordId, action, operatorId, comment);
        return Result.success();
    }

    @GetMapping("/my-logs")
    @ApiLog("Get my audit logs")
    public Result<IPage<AuditLog>> getMyLogs(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            HttpServletRequest request) {
        Long operatorId = getUserIdFromRequest(request);
        if (operatorId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        PageQuery pageQuery = new PageQuery(page, size);
        return Result.success(auditLogService.getOperatorLogs(operatorId, startTime, endTime, pageQuery));
    }

    private LocalDateTime[] defaultRange(LocalDateTime startTime, LocalDateTime endTime, int days) {
        LocalDateTime resolvedEnd = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime resolvedStart = startTime == null ? resolvedEnd.minusDays(days) : startTime;
        return new LocalDateTime[] {resolvedStart, resolvedEnd};
    }

    private void ensureSelfOrAdmin(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Operator id is required");
        }
        if (!currentUserId.equals(targetUserId) && !permissionService.isAdmin(currentUserId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only administrators can view coworker audit logs");
        }
    }

    private Long getUserIdFromRequest(HttpServletRequest request) {
        return parseUserId(request.getAttribute("userId"));
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

    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }
}