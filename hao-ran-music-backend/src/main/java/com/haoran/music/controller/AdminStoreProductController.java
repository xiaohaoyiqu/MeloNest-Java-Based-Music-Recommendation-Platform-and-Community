



package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.StoreProductPolicyRequest;
import com.haoran.music.entity.StoreProductPolicy;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.StoreProductPolicyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/store")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminStoreProductController {
    private final StoreProductPolicyService policyService;

    public AdminStoreProductController(StoreProductPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/products")
    public Result<Map<String, Object>> getProducts(
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(policyService.getAdminProducts(productType, keyword, page, size));
    }

    @PutMapping("/product/{productType}/{productId}/policy")
    @ApiLog("管理员维护商店商品")
    public Result<StoreProductPolicy> changePolicy(
            @PathVariable String productType,
            @PathVariable Long productId,
            @RequestBody StoreProductPolicyRequest request,
            @RequestAttribute("userId") Long operatorId) {
        return Result.success(policyService.changeByAdmin(productType, productId, operatorId,
                request == null ? null : request.getAction(),
                request == null ? null : request.getReason()));
    }
}
