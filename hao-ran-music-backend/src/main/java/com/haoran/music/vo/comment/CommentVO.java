package com.haoran.music.vo.comment;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

   
                      
                      
   
@Data

public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;


    private Long id;


    private String content;


    private Long userId;


    private String username;


    private String userAvatar;


    private Integer targetType;


    private Long targetId;


    private String targetName;


    private String targetCover;


    private Long parentId;


    private String replyToUsername;


    private Integer likeCount;


    private Integer replyCount;


    private Integer isPinned;


    private Boolean isLiked;


    private String ip;


    private LocalDateTime createTime;


    private LocalDateTime updateTime;


    private List<CommentVO> replies;


       
                 
       
    private Integer editCount;


       
             
       
    private Integer remainingEditCount;


       
                                 
       
    private String userRole;


       
              
       
    private Boolean isVip;


       
             
       
    private Boolean isOfficial;


       
             
       
    private Boolean canEdit;


       
           
       
    private List<CommentEditHistoryVO> editHistory;
}

   
                       
   
@Data
class CommentEditHistoryVO {

       
             
       
    private Long id;

       
            
       
    private String content;

       
           
       
    private LocalDateTime editTime;

       
            
       
    private String editorName;
}
