package com.haoran.music.dto.user;

import com.haoran.music.common.enums.VerificationType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

   
                      
                        
   
@Data
public class VerificationSendDTO {

    @NotBlank(message = "验证目标不能为空")
    private String target;

    @NotNull(message = "验证类型不能为空")
    private Integer verificationType;

    private String captchaToken;

    private String captchaCode;
}
