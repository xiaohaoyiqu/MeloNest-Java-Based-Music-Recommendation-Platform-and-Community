   
                      
                        
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
           
                
                          
   
@Data
@TableName("user_equipment_config")
public class UserEquipmentConfig implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
                                                     
       
    private String avatarFrameId;

       
                
       
    private String profileCardId;

       
           
       
    private String profileCardStyle;

       
            
       
    private String badge1Id;

       
            
       
    private String badge2Id;

       
            
       
    private String badge3Id;

       
            
       
    private String badge4Id;

       
            
       
    private String badge5Id;

       
            
       
    private String badge6Id;

       
            
       
    private String badge7Id;

       
            
       
    private String badge8Id;

       
            
       
    private String badge9Id;

       
             
       
    private String badge10Id;

       
               
       
    private String chatBubbleId;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
