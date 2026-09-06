   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
           
                                               
   
@Data
@TableName("user_verification")
public class UserVerification extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

                 
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

                                           
    private Long userId;

                                   
    private Integer verificationType;

                         
    private String target;

                            
    private String code;

                          
    private String codeHash;

                                   
    private Integer status;

                             
    private Integer isUsed;

                
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime usedTime;

                
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

                
    private Integer sendCount;

                  
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSendTime;

                  
    private String clientIp;

                          
    private String userAgent;

                  
    private Integer failCount;

                            
    private Integer isLocked;

                            
    @TableLogic
    private Integer deleted;
}