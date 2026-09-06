   
                      
   
package com.haoran.music.dto.user;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class VipAutoRenewRequest {

    @NotNull(message = "自动续费状态不能为空")
    private Boolean autoRenew;
}
