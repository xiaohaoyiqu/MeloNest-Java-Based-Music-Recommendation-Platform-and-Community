


package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.enums.VipLevel;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.user.VipAutoRenewRequest;
import com.haoran.music.dto.user.VipPurchaseDTO;
import com.haoran.music.service.UserVipService;
import com.haoran.music.vo.user.UserVipVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;




@RestController
@RequestMapping("/user")
public class UserVipController {

    @Resource
    private UserVipService userVipService;

    @ApiLog("获取用户VIP信息")
    @GetMapping("/vip/info")
    public Result<UserVipVO> getVipInfo(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        UserVipVO vipInfo = userVipService.getUserVipInfo(userId);
        return Result.success(vipInfo);
    }

    @ApiLog("购买VIP")
    @PostMapping("/vip/purchase")
    public Result<UserVipVO> purchaseVip(@RequestBody VipPurchaseDTO dto,
                                         @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.error(503, "VIP支付服务尚未接入，不能直接开通VIP权益");
    }

    @ApiLog("续费VIP")
    @PostMapping("/vip/renew")
    public Result<UserVipVO> renewVip(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.error(503, "VIP续费支付服务尚未接入，不能直接延长VIP权益");
    }

    @ApiLog("设置自动续费")
    @PostMapping("/vip/auto-renew")
    public Result<Void> setAutoRenew(@Valid @RequestBody VipAutoRenewRequest request,
                                     @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        userVipService.setAutoRenew(userId, request.getAutoRenew(), null);
        return Result.success();
    }

    @ApiLog("获取VIP特权列表")
    @GetMapping("/vip/privileges")
    public Result<Map<String, Object>> getPrivileges(
            @RequestParam(required = false, defaultValue = "0") Integer level) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> levels = new ArrayList<>();

        Map<String, Object> monthly = new HashMap<>();
        monthly.put("level", 1);
        monthly.put("name", "月度会员");
        monthly.put("price", 10);
        monthly.put("duration", "1个月");
        monthly.put("privileges", Arrays.asList("高音质播放", "无损音质下载（每日100首）", "无广告体验", "专属标识"));
        levels.add(monthly);

        Map<String, Object> quarterly = new HashMap<>();
        quarterly.put("level", 2);
        quarterly.put("name", "季度会员");
        quarterly.put("price", 25);
        quarterly.put("duration", "3个月");
        quarterly.put("privileges", Arrays.asList("月度所有特权", "专属歌单", "优先客服", "生日礼包"));
        levels.add(quarterly);

        Map<String, Object> yearly = new HashMap<>();
        yearly.put("level", 3);
        yearly.put("name", "年度会员");
        yearly.put("price", 100);
        yearly.put("duration", "1年");
        yearly.put("privileges", Arrays.asList("季度所有特权", "母带音质", "4K高清MV", "抢先体验", "年度礼包"));
        levels.add(yearly);

        result.put("levels", levels);
        VipLevel currentLevel = VipLevel.fromCode(level);
        result.put("currentPrivileges", userVipService.getVipPrivileges(currentLevel));
        result.put("purchaseAvailable", false);
        result.put("autoRenewEnableAvailable", false);
        result.put("paymentMessage", "在线会员支付尚未接入，可使用签到活跃值兑换或等待服务开放");
        return Result.success(result);
    }

    @ApiLog("检查VIP状态")
    @GetMapping("/vip/check")
    public Result<Map<String, Object>> checkVipStatus(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("isVip", userVipService.isVip(userId));
        result.put("vipLevel", userVipService.getVipLevel(userId).getCode());
        return Result.success(result);
    }
}
