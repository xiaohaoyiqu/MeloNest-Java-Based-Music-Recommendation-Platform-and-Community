package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;





@Data
public class VerificationVerifyDTO {

    @NotBlank(message = "验证目标不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    private String code;

    private Integer verificationType;
}
