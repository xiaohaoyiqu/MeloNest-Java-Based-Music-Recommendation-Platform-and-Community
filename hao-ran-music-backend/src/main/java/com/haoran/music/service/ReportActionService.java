




package com.haoran.music.service;

import java.util.Map;





public interface ReportActionService {









    Map<String, Object> executeAction(Long reportId, String action, Long reviewerId);








    Boolean hideSong(Long songId, String reason);








    Boolean hideMV(Long mvId, String reason);








    Boolean hideAlbum(Long albumId, String reason);








    Boolean hidePlaylist(Long playlistId, String reason);








    Boolean deleteComment(Long commentId, String reason);









    Boolean banUser(Long userId, String reason, Integer banDays);









    Boolean sendWarning(Long userId, Long reportId, String warningContent);








    Boolean restoreContent(String targetType, Long targetId);









    Integer batchExecuteAction(Long[] reportIds, String action, Long reviewerId);







    Boolean validateReportForAction(Long reportId);
}
