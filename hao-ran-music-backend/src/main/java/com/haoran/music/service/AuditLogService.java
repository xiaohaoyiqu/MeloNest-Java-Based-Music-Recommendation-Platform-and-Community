




package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




public interface AuditLogService {

















    void logAudit(Long recordId, String recordType, String action,
                  String oldStatus, String newStatus,
                  Long operatorId, String operatorName, String comment,
                  String operatorIp, String userAgent,
                  Integer durationSeconds, Boolean success);







    List<AuditLog> getRecordLogs(Long recordId);










    IPage<AuditLog> getOperatorLogs(Long operatorId, LocalDateTime startTime,
                                   LocalDateTime endTime, PageQuery pageQuery);








    Map<String, Object> getAuditStatistics(LocalDateTime startTime, LocalDateTime endTime);








    List<Map<String, Object>> getModeratorWorkStats(LocalDateTime startTime, LocalDateTime endTime);








    Map<String, Object> getAuditTypeStats(LocalDateTime startTime, LocalDateTime endTime);








    long countActions(Long operatorId, String recordType, String action);









    void logAction(Long recordId, String action, Long operatorId, String comment);
}
