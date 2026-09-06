




package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.util.DataMaskingUtil;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CreatorService;
import com.haoran.music.vo.CreatorApplyPrivateVO;
import com.haoran.music.vo.CreatorApplyPublicVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;





@Slf4j
@RestController
@RequestMapping("/creator")
public class CreatorController {

    private final CreatorService creatorService;

    public CreatorController(CreatorService creatorService) {
        this.creatorService = creatorService;
    }





    @ApiLog("申请成为创作者")
    @PostMapping("/apply")
    public Result applyCreator(HttpServletRequest request,
                             @RequestParam String realName,
                             @RequestParam(required = false) String idCardNo,
                             @RequestParam(required = false) String idCardUrl,
                             @RequestParam String phone,
                             @RequestParam String email,
                             @RequestParam String applyReason,
                             @RequestParam(required = false) String worksSample) {
        Long userId = (Long) request.getAttribute("userId");


        if (realName == null || realName.trim().isEmpty()) {
            return Result.error(400, "请输入真实姓名");
        }
        if (phone == null || phone.trim().isEmpty()) {
            return Result.error(400, "请输入联系电话");
        }
        if (!DataMaskingUtil.isValidPhone(phone)) {
            return Result.error(400, "手机号格式不正确");
        }
        if (email != null && !DataMaskingUtil.isValidEmail(email)) {
            return Result.error(400, "邮箱格式不正确");
        }

        return Result.success(creatorService.applyCreator(userId, realName, idCardNo,
                idCardUrl, phone, email, applyReason, worksSample));
    }




    @ApiLog("获取创作者信息")
    @GetMapping("/my")
    public Result getMyCreatorInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(creatorService.getMyCreatorInfo(userId));
    }




    @ApiLog("获取我的申请详情")
    @GetMapping("/my-application")
    public Result<CreatorApplyPublicVO> getMyApplication(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            CreatorApplyPublicVO vo = creatorService.getMyApplication(userId);
            return Result.success(vo);
        } catch (com.haoran.music.common.exception.BusinessException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("event=creator_application_self_query_failed userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
            return Result.error(500, "获取创作者申请失败，请稍后重试");
        }
    }




    @ApiLog("获取创作者收益")
    @GetMapping("/earnings")
    public Result getCreatorEarnings(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(creatorService.getCreatorEarnings(userId));
    }




    @ApiLog("审核创作者申请")
    @PostMapping("/review/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result reviewCreatorApply(@PathVariable Long id,
                                   @RequestAttribute(value = "userId", required = false) Long reviewerId,
                                   @RequestParam Boolean approved,
                                   @RequestParam(required = false) String reviewReason,
                                   @RequestParam(defaultValue = "independent") String creatorType,
                                   @RequestParam(required = false) BigDecimal feeRate) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(creatorService.reviewCreatorApply(id, reviewerId,
                approved, reviewReason, creatorType, feeRate));
    }




    @ApiLog("查看申请详情")
    @GetMapping("/application/{id}")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<CreatorApplyPrivateVO> getApplicationDetail(@PathVariable Long id) {
        try {
            CreatorApplyPrivateVO vo = creatorService.getApplicationDetail(id);
            return Result.success(vo);
        } catch (com.haoran.music.common.exception.BusinessException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("event=creator_application_detail_query_failed applicationId={} errorType={}",
                    id, e.getClass().getSimpleName());
            return Result.error(500, "获取创作者申请详情失败，请稍后重试");
        }
    }




    @ApiLog("获取创作者列表")
    @GetMapping("/list")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result getCreatorList(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String creatorType,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(creatorService.getCreatorList(status, creatorType, page, size));
    }




    @ApiLog("获取待审核创作者申请")
    @GetMapping("/applications/pending")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result getPendingApplications(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(creatorService.getPendingApplications(page, size));
    }




    @ApiLog("获取待审核申请列表（脱敏）")
    @GetMapping("/applications/pending/public")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result getPendingApplicationsPublic(@RequestParam(defaultValue = "1") Integer page,
                                             @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(creatorService.getPendingApplicationsPublic(page, size));
    }




    @ApiLog("更新创作者状态")
    @PostMapping("/{creatorId}/status")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result updateCreatorStatus(@PathVariable Long creatorId,
                                   @RequestParam String status,
                                   @RequestParam String reason) {
        return Result.success(creatorService.updateCreatorStatus(creatorId, status, reason));
    }




    @ApiLog("移除创作者身份")
    @PostMapping("/{creatorId}/remove")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result removeCreator(@PathVariable Long creatorId,
                              @RequestParam String reason) {
        return Result.success(creatorService.removeCreator(creatorId, reason));
    }




    @ApiLog("获取创作者统计数据")
    @GetMapping("/stats")
    public Result getCreatorStats(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(creatorService.getCreatorStats(userId));
    }
}
