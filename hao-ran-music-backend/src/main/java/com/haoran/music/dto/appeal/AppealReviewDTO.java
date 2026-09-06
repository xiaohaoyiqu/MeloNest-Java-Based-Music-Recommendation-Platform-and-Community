package com.haoran.music.dto.appeal;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

   
                      
                         
   
@Data
public class AppealReviewDTO {

       
           
       
    @NotNull(message = "申诉ID不能为空")
    private Long appealId;

       
                                   
       
    @NotBlank(message = "审核结果不能为空")
    private String reviewResult;

       
           
       
    @Size(max = 1000, message = "审核备注不能超过1000字")
    private String reviewRemark;
}
