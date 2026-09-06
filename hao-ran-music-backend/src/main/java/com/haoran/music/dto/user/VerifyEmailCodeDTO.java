



package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class VerifyEmailCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;


    @NotBlank(message = "验证码场景不能为空")
    @Pattern(regexp = "^(register|reset|login|change_email)$", message = "验证码场景不正确")
    private String type;

    @NotBlank(message = "验证码不能为空")
    @Size(min = 6, max = 6, message = "验证码为6位数字")
    @Pattern(regexp = "^\\d{6}$", message = "验证码为6位数字")
    private String verifyCode;
}