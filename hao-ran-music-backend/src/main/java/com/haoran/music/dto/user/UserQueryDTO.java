package com.haoran.music.dto.user;

import com.haoran.music.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

   
                      
                       
   
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;

       
                
       
    private String username;

       
               
       
    private String nickname;

       
           
       
    private Integer status;

       
                                            
                                            
       
    private String accountState;

       
                           
       
    private String keyword;

       
                                                       
       
    private String role;

                                                                  

       
             
                                                                                              
                                                                                              
                                                                           
                                                                                                    
                                                                                      
                                                                                             
                                                                              
                                                               
       
    private Integer userType;

       
              
                                      
       
    private Boolean isVip;

       
             
       
    private LocalDateTime registerTimeStart;

       
             
       
    private LocalDateTime registerTimeEnd;

       
             
       
    private Integer fansCountMin;

       
             
       
    private Integer fansCountMax;

       
             
       
    private Integer creditScoreMin;

       
             
       
    private Integer creditScoreMax;

       
                                       
       
    private Boolean isCreator;

       
                                       
       
    private Boolean isModerator;

       
                                    
       
    private Boolean isOfficial;

       
               
       
    private String verifiedType;

       
               
       
    private String verifiedLevel;
}
