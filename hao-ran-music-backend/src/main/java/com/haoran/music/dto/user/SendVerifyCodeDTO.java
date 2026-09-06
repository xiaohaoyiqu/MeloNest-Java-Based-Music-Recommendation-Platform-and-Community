package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

   
                      
                         
   
@Data
public class SendVerifyCodeDTO {

    private static final long serialVersionUID = 1L;

       
          
       
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

       
                                                                  
                                               
       
    @NotBlank(message = "验证码类型不能为空")
    @Pattern(regexp = "^(register|reset|login|change_phone)$", message = "验证码场景不正确")
    private String type;
}
