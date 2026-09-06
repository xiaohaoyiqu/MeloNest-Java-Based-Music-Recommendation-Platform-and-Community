package com.haoran.music.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                                 
   
@Data
public class CreatorApplyPrivateVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
                                      
       
    private String id;

       
                                      
       
    private String userId;

       
          
       
    private String username;

       
           
       
    private String realName;

       
           
       
    private String idCard;

       
               
       
    private String idCardMasked;

       
             
       
    private String idCardUrl;

       
          
       
    private String phone;

       
         
       
    private String email;

       
           
       
    private String applyType;

       
            
       
    private String creatorType;

       
           
       
    private String reason;

       
           
       
    private String workUrls;

       
         
       
    private String attachments;

       
         
       
    private Integer status;

       
           
       
    private String reviewComment;

       
           
       
    private LocalDateTime applyTime;

       
           
       
    private LocalDateTime reviewTime;

       
                                       
       
    private String reviewerId;

       
            
       
    private String reviewerName;

       
             
       
    private String idCardNo;

       
          
       
    private Long fansCount;

       
          
       
    private Long workCount;
}
