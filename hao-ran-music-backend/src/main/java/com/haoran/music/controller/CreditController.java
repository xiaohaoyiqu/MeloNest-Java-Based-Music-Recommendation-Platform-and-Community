   
                      
                      
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.CreditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

   
         
                  
   
@Slf4j
@RestController
@RequestMapping("/credit")
public class CreditController {

    private final CreditService creditService;

    public CreditController(CreditService creditService) {
        this.creditService = creditService;
    }

       
              
       
    @ApiLog("获取信用分")
    @GetMapping("/my")
    public Result<Integer> getUserCredit(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer creditScore = creditService.getUserCredit(userId);
        return Result.success(creditScore);
    }

       
              
       
    @GetMapping("/level/{score}")
    public Result getCreditLevel(@PathVariable Integer score) {
        return Result.success(creditService.getCreditLevel(score));
    }

       
               
       
    @ApiLog("检查信用分阈值")
    @GetMapping("/check-threshold")
    public Result isBelowThreshold(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(creditService.isBelowThreshold(userId));
    }

       
                
       
    @ApiLog("获取信用分记录")
    @GetMapping("/records")
    public Result getCreditRecords(HttpServletRequest request,
                                @RequestParam String creditType,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer size) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(creditService.getCreditRecords(userId, creditType, page, size));
    }

       
             
       
    @ApiLog("获取信用周期")
    @GetMapping("/period")
    public Result getCurrentPeriod() {
        return Result.success(creditService.getCurrentPeriod());
    }

       
               
       
    @ApiLog("获取重置时间")
    @GetMapping("/next-reset")
    public Result getNextResetTime() {
        return Result.success(creditService.getNextResetTime());
    }

       
               
       
    @ApiLog("管理员调整信用分")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/adjust")
    public Result adjustCredit(@RequestParam Long userId,
                             @RequestParam String creditType,
                             @RequestParam Integer score,
                             @RequestParam String reason,
                             @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(creditService.adjustCredit(userId, creditType,
                score, reason, operatorId));
    }

       
              
       
    @ApiLog("获取信用分统计")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/statistics")
    public Result getCreditStatistics() {
        return Result.success(creditService.getCreditStatistics());
    }

       
                      
       
    @ApiLog("重置信用分")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/reset")
    public Result resetAllCredits() {
        return Result.success(creditService.resetAllCredits());
    }
}
