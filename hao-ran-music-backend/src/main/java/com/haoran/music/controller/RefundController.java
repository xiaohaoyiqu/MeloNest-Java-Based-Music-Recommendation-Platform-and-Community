   
                      
                     
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.RefundService;
import com.haoran.music.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

   
        
                    
   
@Slf4j
@RestController
@RequestMapping("/refund")
public class RefundController {

    private final RefundService refundService;
    private final FeedbackService feedbackService;

    public RefundController(RefundService refundService, FeedbackService feedbackService) {
        this.refundService = refundService;
        this.feedbackService = feedbackService;
    }

       
           
                            
                          
                            
                         
                              
                     
       
    @ApiLog(value = "申请退款", logArgs = false, logReturn = false)
    @PostMapping("/apply")
    public Result applyRefund(HttpServletRequest request,
                             @RequestParam Long orderId,
                             @RequestParam String orderType,
                             @RequestParam String reason,
                             @RequestParam(required = false) String description) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.applyRefund(userId, orderId, orderType, reason, description));
    }

       
               
                            
                         
                     
                       
                     
       
    @ApiLog("获取退款记录")
    @GetMapping("/my")
    public Result getMyRefundRecords(HttpServletRequest request,
                                   @RequestParam(required = false) String status,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.getMyRefundRecords(userId, status, page, size));
    }

       
             
                            
                     
       
    @ApiLog("获取退款统计")
    @GetMapping("/statistics")
    public Result getRefundStatistics(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.getRefundStatistics(userId));
    }

       
             
                            
                          
                       
       
    @ApiLog("检查退款资格")
    @GetMapping("/check-eligible")
    public Result checkRefundEligible(HttpServletRequest request,
                                      @RequestParam Long orderId) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.checkRefundEligible(userId, orderId));
    }

       
             
                           
                   
       
    @ApiLog("获取退款详情")
    @GetMapping("/{id}")
    public Result getRefundDetail(HttpServletRequest request,
                                  @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.getRefundDetail(id, userId));
    }

       
                     
                     
                       
                      
       
    @ApiLog("获取待审核退款")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingRefunds(@RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(refundService.getPendingRefunds(page, size));
    }

       
                  
                           
                              
                           
                               
                                                           
                                        
                   
       
    @ApiLog(value = "审核退款", logArgs = false, logReturn = false)
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{id}")
    public Result reviewRefund(@PathVariable Long id,
                              @RequestAttribute(value = "userId", required = false) Long reviewerId,
                              @RequestParam Boolean approved,
                              @RequestParam(required = false) String reviewReason,
                              @RequestParam(required = false) Boolean isUnreasonable,
                              @RequestParam(required = false) String unreasonableReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(refundService.reviewRefund(id, reviewerId, approved, reviewReason, isUnreasonable, unreasonableReason));
    }

       
                  
                           
                              
                   
       
    @ApiLog("完成退款")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{id}/complete")
    public Result completeRefund(@PathVariable Long id,
                                @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(refundService.completeRefund(id, operatorId));
    }

       
              
                            
                    
       
    @ApiLog("获取退款信用分")
    @GetMapping("/credit")
    public Result getRefundCredit(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.getRefundCredit(userId));
    }

       
             
                            
                           
                   
       
    @ApiLog("取消退款")
    @PostMapping("/{id}/cancel")
    public Result cancelRefund(HttpServletRequest request,
                              @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(refundService.cancelRefund(id, userId));
    }

       
                     
                             
                              
                     
       
    @ApiLog("从反馈创建退款")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/create-from-feedback")
    public Result createFromFeedback(@RequestParam Long feedbackId,
                                   @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(feedbackService.createRefundFromFeedback(feedbackId, operatorId));
    }
}
