package com.haoran.music.dto.attachment;

import lombok.Data;

import javax.validation.constraints.NotBlank;

   
                
  
                      
   
@Data
public class PrivateAttachmentSessionRequest {

       
            
       
    @NotBlank(message = "附件用途不能为空")
    private String purpose;
}
