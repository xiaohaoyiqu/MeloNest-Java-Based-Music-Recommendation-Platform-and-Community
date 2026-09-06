   
                      
                     
   

package com.haoran.music.controller;



import com.haoran.music.enums.UserRole;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

   
        
                    
   
@Slf4j
@RestController
@RequestMapping("/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

       
           
       
    @ApiLog("提交举报")
    @PostMapping("/submit")
    public Result submitReport(HttpServletRequest request,
                             @RequestBody com.haoran.music.dto.ReportSubmitRequest reportRequest) {
        Long reporterId = (Long) request.getAttribute("userId");
        if (reporterId == null) {
            return Result.error(com.haoran.music.common.result.ResultCode.UNAUTHORIZED);
        }
        if (ObjectUtils.isNotEmpty(reportRequest.getAttachmentUrls())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "新举报不再接受附件URL，请先上传私有附件并提交attachmentAssetIds");
        }
        return Result.success(reportService.submitReportWithAssets(reporterId,
                reportRequest.getTargetType(),
                reportRequest.getTargetId(),
                reportRequest.getReportType(),
                reportRequest.getReason(),
                reportRequest.getDescription(),
                reportRequest.getAttachmentAssetIds()));
    }

       
               
       
    @ApiLog("获取举报记录")
    @GetMapping("/my")
    public Result getMyReports(HttpServletRequest request,
                            @RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer size) {
        Long reporterId = (Long) request.getAttribute("userId");
        return Result.success(reportService.getMyReports(reporterId, status, page, size));
    }

       
                  
      
                          
                           
                   
       
    @ApiLog("撤回举报")
    @PostMapping("/withdraw/{reportId}")
    public Result withdrawReport(HttpServletRequest request, @PathVariable Long reportId) {
        Long reporterId = (Long) request.getAttribute("userId");
        if (ObjectUtils.isEmpty(reporterId)) {
            return Result.error(ResultCode.UNAUTHORIZED);
        }
        return Result.success(reportService.withdrawReport(reportId, reporterId));
    }

       
             
       
    @ApiLog("获取举报统计")
    @GetMapping("/statistics")
    public Result getReportStatistics(HttpServletRequest request) {
        Long reporterId = (Long) request.getAttribute("userId");
        return Result.success(reportService.getReportStatistics(reporterId));
    }

       
             
       
    @ApiLog("获取举报详情")
    @GetMapping("/{reportId}")
    public Result getReportDetail(HttpServletRequest request,
                                  @PathVariable Long reportId) {
        Long viewerId = (Long) request.getAttribute("userId");
        return Result.success(reportService.getReportDetail(reportId, viewerId));
    }

       
                
       
    @ApiLog("获取对象举报")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/target")
    public Result getTargetReports(HttpServletRequest request,
                               @RequestParam String targetType,
                               @RequestParam Long targetId,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer size) {
        Long viewerId = (Long) request.getAttribute("userId");
        return Result.success(reportService.getTargetReports(targetType, targetId, page, size, viewerId));
    }

       
                     
       
    @ApiLog("获取待审核举报")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingReports(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(reportService.getPendingReports(page, size));
    }

       
                
       
    @ApiLog("审核举报")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{reportId}")
    public Result reviewReport(@PathVariable Long reportId,
                             @RequestAttribute(value = "userId", required = false) Long reviewerId,
                             @RequestParam Boolean approved,
                             @RequestParam(required = false) String reviewReason,
                             @RequestParam(required = false) String action) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(reportService.reviewReport(reportId, reviewerId,
                approved, reviewReason, action));
    }

       
                  
       
    @ApiLog("发放举报奖励")
    @RequireRole({UserRole.MODERATOR, UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{reportId}/reward")
    public Result grantReportReward(@PathVariable Long reportId) {
        return Result.success(reportService.grantReportReward(reportId));
    }

       
              
       
    @ApiLog("获取举报信用分")
    @GetMapping("/credit")
    public Result getReportCredit(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer credit = reportService.getReportCredit(userId);
        return Result.success(credit);
    }

       
               
       
    @ApiLog("获取举报奖励配置")
    @GetMapping("/reward-config")
    public Result getReportRewardPoints() {
        return Result.success(reportService.getReportRewardPoints());
    }
}
