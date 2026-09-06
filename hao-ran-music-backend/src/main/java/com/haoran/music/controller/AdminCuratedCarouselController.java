   
                      
   
package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.curated.CuratedCarouselRequest;
import com.haoran.music.dto.curated.CuratedReviewRequest;
import com.haoran.music.entity.CuratedCarouselItem;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CuratedCarouselService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

   
                      
   
@RestController
@RequestMapping("/admin/curated-carousel")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminCuratedCarouselController {

    @Resource
    private CuratedCarouselService curatedCarouselService;

    @GetMapping("/items")
    public Result<List<CuratedCarouselItem>> list(
            @RequestParam(required = false) String scene,
            @RequestParam(defaultValue = "false") boolean removed) {
        try {
            return Result.success(curatedCarouselService.listAdminItems(scene, removed));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/items")
    public Result<Long> create(
            @Valid @RequestBody CuratedCarouselRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            return Result.success(curatedCarouselService.create(request, requireOperator(operatorId)));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/items/{itemId}")
    public Result<Void> update(
            @PathVariable Long itemId,
            @Valid @RequestBody CuratedCarouselRequest request,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        try {
            boolean success = curatedCarouselService.update(
                    itemId, request, requireOperator(operatorId));
            return success ? Result.success() : Result.error(404, "轮播内容不存在");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/items/{itemId}/review")
    public Result<Void> review(
            @PathVariable Long itemId,
            @Valid @RequestBody CuratedReviewRequest request,
            @RequestAttribute(value = "userId", required = false) Long reviewerId) {
        try {
            boolean success = curatedCarouselService.review(
                    itemId,
                    request.getApproved(),
                    request.getRemark(),
                    requireOperator(reviewerId));
            return success ? Result.success() : Result.error(404, "轮播内容不存在");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/items/{itemId}")
    @ApiLog("统一轮播内容临时下架")
    public Result<Void> disable(
            @PathVariable Long itemId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        boolean success = curatedCarouselService.disable(itemId, requireOperator(operatorId));
        return success ? Result.success() : Result.error(404, "轮播内容不存在");
    }

    @PostMapping("/items/{itemId}/restore")
    @ApiLog("统一轮播内容重新上架")
    public Result<Void> restore(
            @PathVariable Long itemId,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        boolean success = curatedCarouselService.restore(itemId, requireOperator(operatorId));
        return success ? Result.success() : Result.error(404, "已下架内容中没有这条记录");
    }

    private Long requireOperator(Long operatorId) {
        if (operatorId == null) {
            throw new IllegalArgumentException("请先登录");
        }
        return operatorId;
    }
}
