   
                      
                         
   

package com.haoran.music.vo.blacklist;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

   
            
   
@Data
public class BlacklistUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
              
       
    private Long id;

       
              
       
    private Long blacklistedUserId;

       
              
       
    private String nickname;

       
              
       
    private String avatar;

       
           
       
    private Integer status;

       
                        
       
    private Integer isBanned;

       
           
       
    private String reason;

       
           
       
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

       
             
       
    private Integer isMutual;
}
