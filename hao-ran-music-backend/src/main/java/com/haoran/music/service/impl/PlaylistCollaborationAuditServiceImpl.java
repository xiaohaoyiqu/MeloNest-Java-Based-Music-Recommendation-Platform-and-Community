package com.haoran.music.service.impl;

import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.mapper.PlaylistCollaborationAuditMapper;
import com.haoran.music.service.PlaylistCollaborationAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

   
               
  
                      
   
@Service
public class PlaylistCollaborationAuditServiceImpl implements PlaylistCollaborationAuditService {

    private final PlaylistCollaborationAuditMapper auditMapper;

    public PlaylistCollaborationAuditServiceImpl(PlaylistCollaborationAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String record(Long playlistId, Long actorId, Long targetUserId, String eventType,
                         String beforeSummary, String afterSummary, String reason) {
        if (ObjectUtils.isEmpty(playlistId) || ObjectUtils.isEmpty(actorId)
                || ObjectUtils.isEmpty(eventType)) {
            throw new BusinessException("协作审计事件缺少必要标识");
        }
        String normalizedType = eventType.trim().toLowerCase(Locale.ROOT);
        if (!normalizedType.matches("[a-z0-9_]{2,64}")) {
            throw new BusinessException("协作审计事件类型不合法");
        }
        String eventId = UUID.randomUUID().toString();
        int inserted = auditMapper.insertEvent(eventId, playlistId, actorId, targetUserId,
                normalizedType, truncate(beforeSummary, 2000), truncate(afterSummary, 2000),
                truncate(reason, 500));
        if (inserted != 1) {
            throw new BusinessException("协作审计事件写入失败");
        }
        return eventId;
    }

    @Override
    public List<Map<String, Object>> listRecent(Long playlistId, int limit) {
        if (ObjectUtils.isEmpty(playlistId)) {
            throw new BusinessException("歌单标识不能为空");
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return auditMapper.selectRecentByPlaylist(playlistId, safeLimit);
    }

    private String truncate(String value, int maxLength) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
