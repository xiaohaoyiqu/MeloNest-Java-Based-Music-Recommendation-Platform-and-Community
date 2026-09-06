




package com.haoran.music.service;









public interface ContentAccessLimitService {









    boolean canAccessPaidContent(Long userId, String resourceType, Long resourceId);









    boolean canDownloadPaidContent(Long userId, String resourceType, Long resourceId);









    void recordPaidContentAccess(Long userId, String resourceType, Long resourceId, String accessType);







    boolean checkCreatorBatchAccess(Long userId);








    void recordCreatorContentAccess(Long userId, Long contentId, String contentType);









    int getPaidContentAccessCount(Long userId, String resourceType, Long resourceId);









    int getPaidContentRemainingAccess(Long userId, String resourceType, Long resourceId);









    int getPaidContentDownloadCount(Long userId, String resourceType, Long resourceId);









    int getPaidContentRemainingDownload(Long userId, String resourceType, Long resourceId);
}
