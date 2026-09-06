package com.haoran.music.vo.playlist;

import lombok.Data;

import java.time.LocalDateTime;

   
                      
                     
   
@Data
public class CollaboratorVO {

       
           
       
    private Long userId;

       
           
       
    private String nickname;

       
           
       
    private String avatar;

       
         
       
    private String role;

       
           
       
    private String roleName;

       
             
       
    private Boolean canAdd;

       
             
       
    private Boolean canRemove;

       
             
       
    private Boolean canEdit;

       
           
       
    private LocalDateTime joinedTime;

       
             
       
    private Boolean isInviter;
}
