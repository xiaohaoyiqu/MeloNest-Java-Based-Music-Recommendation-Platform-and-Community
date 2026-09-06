package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

   
               
  
                      
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vip_change_record")
public class VipChangeRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
                                                                               
       
    private String changeType;

       
                          
       
    private Integer changeDays;

       
              
       
    private LocalDateTime beforeExpireTime;

       
              
       
    private LocalDateTime afterExpireTime;

       
                  
       
    private Long relatedId;

       
           
       
    private String reason;

       
               
       
    private String vipLevelBefore;

       
               
       
    private String vipLevelAfter;

       
                                
       
    private String operator;
}
