package com.haoran.music.service;

import java.util.List;
import java.util.Map;






public interface PlaylistCollaborationAuditService {













    String record(Long playlistId, Long actorId, Long targetUserId, String eventType,
                  String beforeSummary, String afterSummary, String reason);








    List<Map<String, Object>> listRecent(Long playlistId, int limit);
}
