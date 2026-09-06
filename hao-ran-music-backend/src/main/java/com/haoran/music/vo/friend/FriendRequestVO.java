   
                      
                      
   

package com.haoran.music.vo.friend;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
         
   
@Data
public class FriendRequestVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Long id;

       
              
       
    private Long fromUserId;

       
             
       
    private String fromUsername;

       
            
       
    private String fromNickname;

       
            
       
    private String fromAvatar;

       
              
       
    private String fromSignature;

       
              
       
    private Integer fromStatus;

       
                          
       
    private Integer fromUserType;

       
                         
       
    private Integer fromIsBanned;

       
              
       
    private Long toUserId;

       
                                                     
       
    private String status;

       
           
       
    private String message;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
