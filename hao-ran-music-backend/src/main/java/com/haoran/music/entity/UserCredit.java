package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
                      
                        
   
@Data
@TableName("user_credit")
public class UserCredit extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    @TableId(type = IdType.AUTO)
    private Long id;

       
           
       
    private Long userId;

       
                 
       
    @TableField("credit_score")
    private Integer creditScore;

       
                                                                                       
       
    @TableField("credit_level")
    private String creditLevel;

       
            
       
    @TableField("total_report_count")
    private Integer totalReportCount;

       
             
       
    @TableField("approved_report_count")
    private Integer approvedReportCount;

       
              
       
    @TableField("rejected_report_count")
    private Integer rejectedReportCount;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
