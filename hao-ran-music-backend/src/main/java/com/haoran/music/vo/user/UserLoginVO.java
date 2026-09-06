package com.haoran.music.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

   
                      
                        
   
@Data
@NoArgsConstructor
@AllArgsConstructor

public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       

    private String token;

       
           
       

    private UserVO userInfo;

       
              
       

    private String tokenType = "Bearer";

       
              
       

    private Long expiresIn = 604800L;

    public UserLoginVO(String token, UserVO userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }
}
