




package com.haoran.music.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;













public interface VideoPostService {










    Map<String, Object> uploadVideoPost(MultipartFile file, Long userId, String content, String topics, Boolean allowComment);








    Long createVideoPost(Long userId, Map<String, Object> postData);







    Map<String, Object> getVideoPostStatus(Long postId, Long viewerId);




    Map<String, Object> retryVideoPostProcessing(Long postId, Long userId);








    String getVideoPlayUrl(Long postId, String quality, Long viewerId);







    String getVideoThumbnailUrl(Long postId, Long viewerId);


    void deliverVideo(Long postId, String quality, Long viewerId,
                      javax.servlet.http.HttpServletResponse response);








    List<Map<String, Object>> getMyVideoPosts(Long userId, Integer status);







    Map<String, Object> getVideoPostStats(Long userId);








    boolean deleteVideoPost(Long postId, Long userId);
}
