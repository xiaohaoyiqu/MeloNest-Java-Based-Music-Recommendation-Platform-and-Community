package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

   
                      
                         
   
@Data
public class UserTypeUpdateDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "用户类型不能为空")
    private Integer userType;

    @Size(max = 500, message = "变更原因不能超过500个字符")
    private String reason;
}
