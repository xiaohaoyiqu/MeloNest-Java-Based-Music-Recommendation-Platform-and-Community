package com.haoran.music.dto.user;

import lombok.Data;
import javax.validation.constraints.Email;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDate;

   
                      
                       
   

@Data
public class UserUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

       
         
       
    @Size(max = 30, message = "昵称长度不能超过30个字符")
    private String nickname;

       
          
       
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

       
         
       
    @Email(message = "邮箱格式不正确")
    private String email;

       
            
       
    private String avatar;

       
           
       
    @Size(max = 200, message = "个性签名长度不能超过200个字符")
    private String signature;

       
                       
       
    private Integer gender;

       
         
       
    private LocalDate birthday;

       
              
       
    private String wallpaper;

       
                   
       
    private String oldPassword;

                              
    @Size(min = 4, max = 10, message = "手机号验证码长度不正确")
    private String phoneVerifyCode;

                            
    @Size(min = 4, max = 10, message = "邮箱验证码长度不正确")
    private String emailVerifyCode;

       
          
       
    @Size(min = 6, max = 20, message = "密码长度为6-20个字符")
    private String newPassword;
}
