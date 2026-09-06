


package com.haoran.music.dto.appeal;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;




@Data
public class AccountRestrictionAppealSubmitDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "验证渠道不能为空")
    @Size(max = 10, message = "验证渠道不正确")
    private String channel;

    @NotBlank(message = "已绑定手机号或邮箱不能为空")
    @Size(max = 128, message = "已绑定手机号或邮箱过长")
    private String contact;

    @NotBlank(message = "验证码不能为空")
    @Size(max = 12, message = "验证码格式不正确")
    private String verifyCode;

    @NotBlank(message = "申诉理由不能为空")
    @Size(min = 10, max = 2000, message = "申诉理由长度必须为10到2000字")
    private String appealReason;
}
