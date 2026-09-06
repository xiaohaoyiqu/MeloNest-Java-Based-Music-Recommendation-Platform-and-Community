   
                      
                     
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.WithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;

   
        
                     
   
@Slf4j
@RestController
@RequestMapping("/withdraw")
public class WithdrawController {

    private final WithdrawService withdrawService;

    public WithdrawController(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

       
                
       
    @ApiLog("申请提现")
    @PostMapping("/apply")
    public Result applyWithdraw(HttpServletRequest request,
                              @RequestParam BigDecimal amount,
                              @RequestParam String withdrawType,
                              @RequestParam String withdrawAccount,
                              @RequestParam String withdrawName) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(withdrawService.applyWithdraw(creatorId, amount,
                withdrawType, withdrawAccount, withdrawName));
    }

       
               
       
    @ApiLog("获取提现记录")
    @GetMapping("/my")
    public Result getMyWithdrawRecords(HttpServletRequest request,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer size) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(withdrawService.getMyWithdrawRecords(creatorId, status, page, size));
    }

       
             
       
    @ApiLog("获取提现统计")
    @GetMapping("/statistics")
    public Result getWithdrawStatistics(HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(withdrawService.getWithdrawStatistics(creatorId));
    }

       
              
       
    @ApiLog("获取可提现金额")
    @GetMapping("/available")
    public Result getAvailableAmount(HttpServletRequest request) {
        Long creatorId = (Long) request.getAttribute("userId");
        return Result.success(withdrawService.getAvailableAmount(creatorId));
    }

       
                     
       
    @ApiLog("获取待审核提现")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result getPendingWithdraws(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(withdrawService.getPendingWithdraws(page, size));
    }

       
                  
       
    @ApiLog("审核提现")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{id}")
    public Result reviewWithdraw(@PathVariable Long id,
                               @RequestAttribute(value = "userId", required = false) Long reviewerId,
                               @RequestParam Boolean approved,
                               @RequestParam(required = false) String reviewReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(withdrawService.reviewWithdraw(id, reviewerId,
                approved, reviewReason));
    }

       
                  
       
    @ApiLog("完成提现")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/{id}/complete")
    public Result completeWithdraw(@PathVariable Long id,
                                 @RequestParam String transactionId,
                                 @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(withdrawService.completeWithdraw(id, transactionId, operatorId));
    }

       
             
       
    @ApiLog("获取提现详情")
    @GetMapping("/{id}")
    public Result getWithdrawDetail(HttpServletRequest request,
                                    @PathVariable Long id) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(withdrawService.getWithdrawDetail(id, userId));
    }
}
