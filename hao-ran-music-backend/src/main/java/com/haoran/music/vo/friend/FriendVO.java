   
                      
                    
   

package com.haoran.music.vo.friend;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
       
   
@Data
public class FriendVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Long userId;

       
          
       
    private String username;

       
         
       
    private String nickname;

       
         
       
    private String avatar;

       
           
       
    private String signature;

       
           
       
    private String role;

       
           
       
    private Integer status;

       
                       
       
    private Integer userType;

       
                      
       
    private Integer isBanned;

       
            
       
    private Boolean isCreator;

       
            
       
    private String creatorStatus;

       
            
       
    private String creatorType;

       
            
       
    private Boolean isVip;

       
            
       
    private Integer vipLevel;

       
          
       
    private Integer creditScore;

       
           
       
    private String friendGroup;

       
           
       
    private String remark;

       
           
       
    private String specialMark;

       
           
       
    private String relationType;                                 

       
           
       
    private Boolean isMutual;

       
          
       
    private Long fansCount;

       
          
       
    private Long followingCount;

       
             
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime friendSince;
}
