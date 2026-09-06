package com.haoran.music.dto.user;

import com.haoran.music.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

   
                      
                                  
   
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPasswordQueryDTO extends PageQuery {

    private static final long serialVersionUID = 1L;

       
                
       
    private String username;

       
                 
       
    private Long userId;
}
