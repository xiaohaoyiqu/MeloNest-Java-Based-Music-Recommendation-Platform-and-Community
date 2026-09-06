




package com.haoran.music.service;

import java.util.Map;
import java.util.List;





public interface FeedbackService {














    Map<String, Object> submitFeedback(Long userId, String feedbackType,
                                      Long orderId, String orderType,
                                      String title, String content,
                                      String attachmentUrls);













    Map<String, Object> submitFeedbackWithAssets(Long userId, String feedbackType,
                                                 Long orderId, String orderType,
                                                 String title, String content,
                                                 List<Long> attachmentAssetIds);











    Map<String, Object> getMyFeedbacks(Long userId, String feedbackType,
                                      String status, Integer page, Integer size);









    Map<String, Object> getPendingFeedbacks(String feedbackType, Integer page, Integer size);










    Map<String, Object> handleFeedback(Long feedbackId, Long handlerId,
                                      String status, String handleResult);







    Map<String, Object> getFeedbackDetail(Long feedbackId);








    Map<String, Object> getFeedbackDetail(Long feedbackId, Long viewerId);









    Boolean closeFeedback(Long feedbackId, Long handlerId, String closeReason);








    Long createRefundFromFeedback(Long feedbackId, Long operatorId);







    Map<String, Object> getFeedbackStatistics(Long userId);









    Map<String, Object> getAdminFeedbackStatistics(Long handlerId,
                                                   String startDate, String endDate);










    Integer batchHandleFeedback(Long[] feedbackIds, Long handlerId,
                               String status, String handleResult);







    Map<String, Object> getHotFeedbackIssues(Integer limit);
}
