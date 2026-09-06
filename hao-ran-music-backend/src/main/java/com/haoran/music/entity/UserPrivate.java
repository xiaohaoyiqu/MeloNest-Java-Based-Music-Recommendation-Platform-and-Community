   
                      
                              
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

   
           
                    
   
@Data
@TableName("user_sensitive_profile")
public class UserPrivate {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
                  
       
    private byte[] realName;

       
                  
       
    private byte[] idCard;

       
                 
       
    private byte[] phoneEncrypted;

       
                
       
    private byte[] emailEncrypted;

       
                
       
    private String provinceCode;

       
                
       
    private String cityCode;

       
                
       
    private String districtCode;

       
                  
       
    private byte[] addressDetail;

       
                          
       
    private Integer realNameVerified;

       
           
       
    private LocalDateTime verifyTime;

       
                              
       
    private String verifyMethod;

       
                  
       
    private byte[] bankName;

       
                  
       
    private byte[] bankAccount;

       
                  
       
    private byte[] bankAccountName;

       
                  
       
    private byte[] securityQuestion;

       
                  
       
    private byte[] securityAnswer;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

       
                        
       
    @TableLogic
    private Integer deleted;
}
