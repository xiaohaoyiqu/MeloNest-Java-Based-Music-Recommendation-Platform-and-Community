package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;

   
                      
                               
   
@Data
public class AccountBindDTO {

    @NotBlank(message = "邮箱地址不能为空")
    private String email;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
