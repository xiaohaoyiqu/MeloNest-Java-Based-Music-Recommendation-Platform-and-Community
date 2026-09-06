package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;





@Data
public class ResetPasswordDTO {

    private static final long serialVersionUID = 1L;




    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;




    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    private String verifyCode;




    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度为6-20位")
    private String newPassword;




    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
