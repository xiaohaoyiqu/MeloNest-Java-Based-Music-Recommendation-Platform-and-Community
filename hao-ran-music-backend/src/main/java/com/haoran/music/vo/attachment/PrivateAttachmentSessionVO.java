package com.haoran.music.vo.attachment;

import lombok.Data;

import java.time.LocalDateTime;

   
              
  
                      
   
@Data
public class PrivateAttachmentSessionVO {
    private String sessionToken;
    private String purpose;
    private Integer maxFiles;
    private LocalDateTime expiresAt;
}
