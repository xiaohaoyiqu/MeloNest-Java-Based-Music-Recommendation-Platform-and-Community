




package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.MusicPost;

import java.util.List;
import java.util.Map;




public interface MusicPostService extends IService<MusicPost> {












    IPage<Object> getPosts(String timeRange, String type, Long topicId, Long currentUserId, Integer page, Integer size);













    Long createPost(Long userId, String content, String images, String resourceType,
                    Long resourceId, String topics, String visibility, Boolean allowComment);










    Long createListenDiary(Long userId, String content, String images, String topics);








    Boolean deletePost(Long postId, Long userId);









    Boolean updatePost(Long postId, Long userId, String content);









    Boolean updatePostCommentSetting(Long postId, Long userId, Boolean allowComment);









    Boolean updateOfficialCommentClosed(Long postId, Long operatorId, Boolean closed);









    Long autoCreateListenDiary(Long userId, Long songId, String songName, String artistName);








    Boolean likePost(Long postId, Long userId);








    Boolean unlikePost(Long postId, Long userId);








    Long createVideoPost(Long userId, java.util.Map<String, Object> postData);












    IPage<Object> getUserPosts(Long userId, Long currentUserId, Integer page, Integer size);








    Map<String, Object> getUserPostStats(Long userId, Long currentUserId);











    IPage<Object> getUserPostsByType(Long userId, String postType, Long currentUserId, Integer page, Integer size);








    Integer batchDeletePosts(List<Long> postIds, Long userId);








    Map<String, Object> getPostDetail(Long postId, Long currentUserId);


    void requirePostReadable(Long postId, Long currentUserId);


    void requirePostOwnerOrModerator(Long postId, Long currentUserId);










    List<Map<String, Object>> getMyVideoPosts(Long userId, Integer status);







    Map<String, Object> getVideoPostStats(Long userId);








    boolean deleteVideoPost(Long postId, Long userId);
}
