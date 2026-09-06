


package com.haoran.music.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.dto.creator.CreatorWorkDTO;
import com.haoran.music.entity.CreatorWork;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CreatorWorkService;
import com.haoran.music.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/creator/work")
@RequiredArgsConstructor
public class CreatorWorkController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final CreatorWorkService creatorWorkService;
    private final PermissionService permissionService;

    @ApiLog("submit creator work")
    @PostMapping("/submit")
    public Result<Long> submitWork(@RequestAttribute(value = "userId", required = false) Long userId,
                                   @Valid @RequestBody CreatorWorkDTO dto) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        try {
            return Result.success(creatorWorkService.submitWork(userId, dto));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("event=creator_work_submission_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return Result.error(500, "submit creator work failed, please try again later");
        }
    }

    @ApiLog("list my creator works")
    @GetMapping("/my")
    public Result<IPage<CreatorWork>> getMyWorks(@RequestAttribute(value = "userId", required = false) Long userId,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(defaultValue = "20") Integer size) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        IPage<CreatorWork> result = creatorWorkService.pageWorks(normalizePage(page), normalizeSize(size), status, userId);
        return Result.success(result);
    }

    @ApiLog("creator work pending count")
    @GetMapping("/pending-count")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Long> getPendingCount() {
        return Result.success(creatorWorkService.getPendingCount());
    }

    @ApiLog("page creator works")
    @GetMapping("/page")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<IPage<CreatorWork>> pageWorks(@RequestParam(required = false) Integer current,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(defaultValue = "20") Integer size,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) Long userId) {
        Integer pageNo = current != null ? current : page;
        IPage<CreatorWork> result = creatorWorkService.pageWorks(normalizePage(pageNo), normalizeSize(size), status, userId);
        return Result.success(result);
    }

    @ApiLog("check creator work submit availability")
    @GetMapping("/can-submit")
    public Result<Boolean> canSubmit(@RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(creatorWorkService.canSubmit(userId));
    }

    @ApiLog("get creator work detail")
    @GetMapping("/{workId}")
    public Result<CreatorWork> getWorkDetail(@PathVariable Long workId,
                                             @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        CreatorWork work = creatorWorkService.getById(workId);
        if (work == null || isDeleted(work)) {
            return Result.error(404, "work does not exist");
        }
        if (!canAccessWork(work, userId)) {
            return Result.error(403, "permission denied");
        }
        return Result.success(work);
    }

    @ApiLog("update creator work")
    @PutMapping("/{workId}")
    public Result<Boolean> updateWork(@PathVariable Long workId,
                                      @RequestAttribute(value = "userId", required = false) Long userId,
                                      @Valid @RequestBody CreatorWorkDTO dto) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        try {
            return Result.success(creatorWorkService.updateWork(workId, userId, dto));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("event=creator_work_update_failed workId={} userId={} errorType={}",
                    workId, userId, e.getClass().getSimpleName());
            return Result.error(500, "update creator work failed, please try again later");
        }
    }

    @ApiLog("delete creator work")
    @DeleteMapping("/{workId}")
    public Result<Boolean> deleteWork(@PathVariable Long workId,
                                      @RequestAttribute(value = "userId", required = false) Long userId) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        try {
            return Result.success(creatorWorkService.deleteWork(workId, userId));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("event=creator_work_delete_failed workId={} userId={} errorType={}",
                    workId, userId, e.getClass().getSimpleName());
            return Result.error(500, "delete creator work failed, please try again later");
        }
    }

    @ApiLog("creator work policy update")
    @PostMapping("/{workId}/policy-update")
    public Result<Boolean> policyUpdate(@PathVariable Long workId,
                                        @RequestAttribute(value = "userId", required = false) Long userId,
                                        @RequestParam String reason) {
        if (userId == null) {
            return Result.error(401, "Unauthorized");
        }
        CreatorWork work = creatorWorkService.getById(workId);
        if (work == null || isDeleted(work)) {
            return Result.error(404, "work does not exist");
        }
        if (!work.getUserId().equals(userId) && !isModerator(userId)) {
            return Result.error(403, "permission denied");
        }
        try {
            creatorWorkService.handlePolicyUpdate(workId, reason);
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("event=creator_work_policy_update_failed workId={} userId={} errorType={}",
                    workId, userId, e.getClass().getSimpleName());
            return Result.error(500, "policy update failed, please try again later");
        }
    }

    @ApiLog("review creator work")
    @PostMapping("/{workId}/review")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Boolean> reviewWork(@PathVariable Long workId,
                                      @RequestAttribute(value = "userId", required = false) Long reviewerId,
                                      @RequestParam Integer status,
                                      @RequestParam(required = false) String reviewReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        if (status == null || (status != 1 && status != 2)) {
            return Result.error(400, "status must be 1 or 2");
        }
        try {
            creatorWorkService.reviewWork(workId, reviewerId, status, reviewReason);
            return Result.success(true);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("event=creator_work_review_failed workId={} reviewerId={} errorType={}",
                    workId, reviewerId, e.getClass().getSimpleName());
            return Result.error(500, "review creator work failed, please try again later");
        }
    }

    private boolean canAccessWork(CreatorWork work, Long userId) {
        if (work.getUserId().equals(userId)) {
            return true;
        }
        if (Integer.valueOf(1).equals(work.getStatus())) {
            return true;
        }
        return isModerator(userId);
    }

    private boolean isModerator(Long userId) {
        String role = permissionService.getUserRole(userId);
        return UserRole.canModerate(role);
    }

    private boolean isDeleted(CreatorWork work) {
        return work.getDeleted() != null && work.getDeleted() == 1;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
