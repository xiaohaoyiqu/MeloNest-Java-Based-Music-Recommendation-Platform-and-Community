package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

   
                      
                          
   
@Data
@TableName("payment_code_log")
public class PaymentCodeLog extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long configId;

       
            
       
    private Long operatorId;

       
                                                            
       
    private String actionType;

       
             
       
    private String oldUrl;

       
             
       
    private String newUrl;

       
             
       
    private String oldMd5;

       
             
       
    private String newMd5;

       
          
       
    private String verifyCode;

       
         
       
    private String remark;

       
           
       
    private String ipAddress;
}
