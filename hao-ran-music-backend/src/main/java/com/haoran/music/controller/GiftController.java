package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.gift.MarketplaceGiftCreateDTO;
import com.haoran.music.dto.gift.VipGiftCreateDTO;
import com.haoran.music.service.VipGiftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;





@RestController
@RequestMapping("/gift")
public class GiftController {

    private final VipGiftService vipGiftService;

    public GiftController(VipGiftService vipGiftService) {
        this.vipGiftService = vipGiftService;
    }








    @ApiLog("创建VIP赠礼")
    @PostMapping("/vip")
    public Result createVipGift(@Valid @RequestBody VipGiftCreateDTO dto,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(vipGiftService.createVipGift(userId, dto));
    }








    @ApiLog("创建交易市场商品赠礼")
    @PostMapping("/marketplace")
    public Result createMarketplaceGift(@Valid @RequestBody MarketplaceGiftCreateDTO dto,
                                        @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(vipGiftService.createMarketplaceGift(userId, dto));
    }








    @ApiLog("获取赠礼详情")
    @GetMapping("/{giftOrderId}")
    public Result getGiftDetail(@PathVariable Long giftOrderId,
                                @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(vipGiftService.getGiftDetail(userId, giftOrderId));
    }










    @ApiLog("获取我送出的赠礼")
    @GetMapping("/sent")
    public Result getSentGifts(@RequestParam(required = false) String status,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size,
                               @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(vipGiftService.getSentGifts(userId, status, page, size));
    }










    @ApiLog("获取我收到的赠礼")
    @GetMapping("/received")
    public Result getReceivedGifts(@RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size,
                                   @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(vipGiftService.getReceivedGifts(userId, status, page, size));
    }
}
