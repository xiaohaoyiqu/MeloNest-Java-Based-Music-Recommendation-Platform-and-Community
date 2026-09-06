package com.haoran.music.dto.artist;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;





@Data
public class ArtistApplicationDTO {

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 100, message = "真实姓名不能超过100个字符")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String phone;

    @Email(message = "请输入正确的邮箱格式")
    private String email;

    @NotBlank(message = "个人简介不能为空")
    @Size(max = 1000, message = "个人简介不能超过1000个字符")
    private String introduction;

    private String demoWorks;

    private Integer applicationType;
}
