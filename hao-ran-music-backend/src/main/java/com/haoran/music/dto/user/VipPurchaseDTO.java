package com.haoran.music.dto.user;

import com.haoran.music.common.enums.VipLevel;
import lombok.Data;

import javax.validation.constraints.NotNull;





@Data
public class VipPurchaseDTO {

    @NotNull(message = "VIP等级不能为空")
    private Integer vipLevel;

    private String paymentMethod;

    private Boolean autoRenew = false;

    private Integer renewCycle;
}
