package com.haoran.music.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

   
                      
                         
   
@Data
public class ReportSubmitRequest {

       
             
       
    @NotBlank(message = "举报对象类型不能为空")
    private String targetType;

       
             
       
    @NotNull(message = "举报对象ID不能为空")
    private Long targetId;

       
           
       
    @NotBlank(message = "举报类型不能为空")
    private String reportType;

       
           
       
    @NotBlank(message = "举报原因不能为空")
    private String reason;

       
           
       
    private String description;

       
             
       
    private String attachmentUrls;

       
                          
       
    @Size(max = 5, message = "举报附件最多5个")
    private List<Long> attachmentAssetIds;
}
