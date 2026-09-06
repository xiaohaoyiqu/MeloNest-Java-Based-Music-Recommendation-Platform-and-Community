package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.VipGiftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;





@RestController
@RequestMapping("/admin/gift")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminGiftController {

    private final VipGiftService vipGiftService;

    public AdminGiftController(VipGiftService vipGiftService) {
        this.vipGiftService = vipGiftService;
    }









    @ApiLog("获取后台赠礼订单")
    @GetMapping("/orders")
    public Result getGiftOrders(@RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(vipGiftService.getAdminGiftOrders(status, page, size));
    }








    @ApiLog("重试VIP赠礼权益发放")
    @PostMapping("/vip/{giftOrderId}/retry")
    public Result retryVipGift(@PathVariable Long giftOrderId,
                               @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(vipGiftService.retryVipGift(giftOrderId, operatorId));
    }








    @ApiLog("重试赠礼交付")
    @PostMapping("/{giftOrderId}/retry")
    public Result retryGift(@PathVariable Long giftOrderId,
                            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(vipGiftService.retryGift(giftOrderId, operatorId));
    }
}
