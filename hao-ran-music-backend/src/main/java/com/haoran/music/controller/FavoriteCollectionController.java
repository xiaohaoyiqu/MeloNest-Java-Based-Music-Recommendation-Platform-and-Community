package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.favorite.FavoriteGroupItemDTO;
import com.haoran.music.dto.favorite.FavoriteGroupOrderDTO;
import com.haoran.music.dto.favorite.FavoriteGroupSaveDTO;
import com.haoran.music.service.FavoriteCollectionService;
import com.haoran.music.vo.favorite.FavoriteGroupStateVO;
import com.haoran.music.vo.favorite.FavoriteGroupVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;


@RestController
@RequestMapping("/favorite-groups")
public class FavoriteCollectionController {
    @Resource
    private FavoriteCollectionService favoriteCollectionService;

    @GetMapping
    @ApiLog("读取收藏分组")
    public Result<FavoriteGroupStateVO> getState(
            @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) return Result.error(401, "请先登录");
        return Result.success(favoriteCollectionService.getState(userId));
    }

    @PostMapping
    @ApiLog("创建收藏分组")
    @RateLimit(maxRequests = 20, timeWindowSeconds = 3600, operation = "createFavoriteGroup",
            message = "新建收藏分组太频繁，请稍后再试")
    public Result<FavoriteGroupVO> create(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid FavoriteGroupSaveDTO request) {
        if (userId == null) return Result.error(401, "请先登录");
        return Result.success(favoriteCollectionService.createGroup(userId, request.getName()));
    }

    @PutMapping("/{groupId}")
    @ApiLog("重命名收藏分组")
    @RateLimit(maxRequests = 60, timeWindowSeconds = 60, operation = "renameFavoriteGroup",
            message = "修改分组太频繁，请稍后再试")
    public Result<Void> rename(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long groupId,
            @RequestBody @Valid FavoriteGroupSaveDTO request) {
        if (userId == null) return Result.error(401, "请先登录");
        favoriteCollectionService.renameGroup(userId, groupId, request.getName());
        return Result.success();
    }

    @PutMapping("/order")
    @ApiLog("调整收藏分组顺序")
    @RateLimit(maxRequests = 60, timeWindowSeconds = 60, operation = "reorderFavoriteGroups",
            message = "调整分组太频繁，请稍后再试")
    public Result<Void> reorder(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @RequestBody @Valid FavoriteGroupOrderDTO request) {
        if (userId == null) return Result.error(401, "请先登录");
        favoriteCollectionService.reorderGroups(userId, request.getGroupIds());
        return Result.success();
    }

    @DeleteMapping("/{groupId}")
    @ApiLog("删除收藏分组")
    @RateLimit(maxRequests = 30, timeWindowSeconds = 60, operation = "deleteFavoriteGroup",
            message = "移除分组太频繁，请稍后再试")
    public Result<Void> delete(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long groupId) {
        if (userId == null) return Result.error(401, "请先登录");
        favoriteCollectionService.deleteGroup(userId, groupId);
        return Result.success();
    }

    @PutMapping("/{groupId}/items")
    @ApiLog("整理收藏分组")
    @RateLimit(maxRequests = 120, timeWindowSeconds = 60, operation = "assignFavoriteGroup",
            message = "整理动作太快，请稍后继续")
    public Result<Void> assign(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable Long groupId,
            @RequestBody @Valid FavoriteGroupItemDTO request) {
        if (userId == null) return Result.error(401, "请先登录");
        favoriteCollectionService.assignItem(userId, groupId, request.getResourceType(), request.getResourceId());
        return Result.success();
    }

    @DeleteMapping("/items/{resourceType}/{resourceId}")
    @ApiLog("移出收藏分组")
    @RateLimit(maxRequests = 120, timeWindowSeconds = 60, operation = "removeFavoriteGroupItem",
            message = "整理动作太快，请稍后继续")
    public Result<Void> removeItem(
            @RequestAttribute(value = "userId", required = false) Long userId,
            @PathVariable String resourceType,
            @PathVariable Long resourceId) {
        if (userId == null) return Result.error(401, "请先登录");
        favoriteCollectionService.removeItem(userId, resourceType, resourceId);
        return Result.success();
    }
}
