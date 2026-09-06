   
                      
                          
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
             
   
@Data
@TableName("credit_record")
public class CreditRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

       
           
       
    private Long userId;

       
            
                                                
                                                          
                                                                
       
    private String creditType;

       
                        
       
    private Integer score;

       
           
       
    private String reason;

       
                     
       
    private Long operatorId;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
