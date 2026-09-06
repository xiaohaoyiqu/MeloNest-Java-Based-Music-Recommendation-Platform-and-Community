package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.dto.comment.CommentCreateDTO;
import com.haoran.music.entity.Comment;
import com.haoran.music.vo.comment.CommentVO;
import com.haoran.music.dto.comment.CommentEditHistoryVO;

import java.util.List;

   
                      
                      
   
public interface CommentService extends IService<Comment> {

       
                 
      
                            
                                         
                   
       
    CommentVO getCommentById(Long commentId, Long userId);

       
                    
      
                                                  
                             
                             
                                 
                     
       
    IPage<CommentVO> pageComments(Integer targetType, Long targetId, PageQuery pageQuery, Long userId);

       
                 
      
                             
                            
                                 
                      
       
    IPage<CommentVO> getReplies(Long commentId, PageQuery pageQuery, Long userId);

       
           
      
                         
                         
                   
       
    Long createComment(Long userId, CommentCreateDTO dto);

       
           
      
                             
                             
                   
       
    Boolean deleteComment(Long userId, Long commentId);

       
           
      
                            
                            
                   
       
    Boolean likeComment(Long userId, Long commentId);

       
             
      
                            
                            
                   
       
    Boolean unlikeComment(Long userId, Long commentId);

       
             
      
                             
                             
                                 
                     
       
    List<CommentVO> getHotComments(Integer targetType, Integer limit, Long userId);

       
                
      
                         
                            
                     
       
    IPage<CommentVO> getUserComments(Long userId, PageQuery pageQuery);

       
                 
      
                            
                            
                            
                   
       
    Boolean editComment(Long userId, Long commentId, String newContent);

       
               
      
                            
                     
       
    List<CommentEditHistoryVO> getCommentEditHistory(Long commentId, Long userId);

       
           
      
                            
                            
                            
                              
                   
       
    Boolean reportComment(Long userId, Long commentId, String reason, String description);

                                                          

       
                       
                                                      
      
                                                  
                             
                             
                                          
                     
       
    List<CommentVO> getQualityComments(Integer targetType, Long targetId, Integer limit, Long userId);

       
                       
      
                                                  
                             
                             
                               
                     
       
    List<CommentVO> getFriendComments(Integer targetType, Long targetId, Integer limit, Long userId);

       
                          
                       
      
                                                  
                             
                             
                     
       
    List<com.haoran.music.vo.user.UserVO> getCommentRecommendedUsers(
            Integer targetType, Long targetId, Integer limit, Long userId);
}
