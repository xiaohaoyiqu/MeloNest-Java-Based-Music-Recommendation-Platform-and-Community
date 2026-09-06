   
                      
   

package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.config.VipConfig;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.StoreProductPolicyRequest;
import com.haoran.music.entity.StoreProductPolicy;
import com.haoran.music.service.StoreProductPolicyService;
import com.haoran.music.vo.StoreProductVO;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/store/product")
public class StoreProductController {
    private final StoreProductPolicyService policyService;
    private final VipConfig vipConfig;

    public StoreProductController(StoreProductPolicyService policyService, VipConfig vipConfig) {
        this.policyService = policyService;
        this.vipConfig = vipConfig;
    }

    @GetMapping("/vip-plans")
    public Result<List<Map<String, Object>>> getVipPlans() {
        List<Map<String, Object>> plans = new ArrayList<>();
        addVipPlan(plans, 1L, "month", false);
        addVipPlan(plans, 3L, "quarter", true);
        addVipPlan(plans, 12L, "year", false);
        return Result.success(plans);
    }

    @GetMapping("/{productType}/{productId}")
    public Result<StoreProductVO> getProduct(@PathVariable String productType,
                                             @PathVariable Long productId,
                                             @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        StoreProductVO product = policyService.getProduct(productType, productId);
        if (product.getSellerId() == null || !userId.equals(product.getSellerId())) {
            return Result.error(403, "这不是你打理的商品");
        }
        return Result.success(product);
    }

    @PutMapping("/{productType}/{productId}/owner-policy")
    @ApiLog("作者调整商店商品状态")
    public Result<StoreProductPolicy> changeOwnerPolicy(
            @PathVariable String productType,
            @PathVariable Long productId,
            @RequestBody StoreProductPolicyRequest request,
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(policyService.changeByOwner(productType, productId, userId,
                request == null ? null : request.getAction(),
                request == null ? null : request.getReason()));
    }

    private void addVipPlan(List<Map<String, Object>> plans, Long productId,
                            String type, boolean recommended) {
        VipConfig.VipPrice price = vipConfig.getPrice(type);
        if (price == null || price.getDays() == null || price.getDays() <= 0
                || vipConfig.getPriceInYuan(type).signum() <= 0) {
            return;
        }
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("productId", productId);
        plan.put("type", type);
        plan.put("name", price.getName());
        plan.put("days", price.getDays());
        plan.put("price", vipConfig.getPriceInYuan(type));
        plan.put("recommended", recommended);
        plans.add(plan);
    }
}
