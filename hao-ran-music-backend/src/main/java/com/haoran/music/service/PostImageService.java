




package com.haoran.music.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;










public interface PostImageService {








    Map<String, String> uploadPostImage(MultipartFile file, Long userId);








    List<Map<String, String>> uploadPostImages(List<MultipartFile> files, Long userId);









    Map<String, String> uploadPostImageWithId(MultipartFile file, Long postId, Integer index);







    List<Map<String, Object>> getPostImages(Long postId, Long viewerId);








    String getOriginalImageUrl(Long postId, Integer index, Long viewerId);


    void deliverPostImage(Long postId, Integer index, String variant, Long viewerId,
                          javax.servlet.http.HttpServletResponse response);







    boolean deletePostImages(Long postId);
}
