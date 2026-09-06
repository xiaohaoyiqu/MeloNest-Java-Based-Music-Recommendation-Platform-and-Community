package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("playlist_collaborator")
public class PlaylistCollaborator implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long playlistId;

       
              
       
    private Long userId;

       
                             
       
    private String role;

       
               
       
    private Integer canAdd;

       
               
       
    private Integer canRemove;

       
                 
       
    private Integer canEdit;

       
           
       
    private LocalDateTime joinedTime;

       
            
       
    private Long invitedBy;

       
                                   
       
    private String status;
       
                        
       
    @TableLogic
    private Integer deleted;

}
