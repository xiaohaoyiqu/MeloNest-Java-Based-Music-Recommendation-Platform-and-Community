   
                      
                                         
  
        
                                                   
                                                 
   
package com.haoran.music.controller;



import com.haoran.music.enums.UserRole;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.VipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

   
            
                                  
   
@Slf4j
@RestController
@RequestMapping("/admin/vip")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class AdminVipController {

    private final VipService vipService;

    public AdminVipController(VipService vipService) {
        this.vipService = vipService;
    }

       
                        
      
                     
                       
                    
  
    @ApiLog("获取待审核VIP申请")
    @GetMapping("/applications/pending")
    public Result getPendingVipApplies(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(vipService.getPendingVipApplies(page, size));
    }

       
                      
      
                     
                              
                           
                                   
                   
  
    @ApiLog("审核VIP申请")
    @PostMapping("/creator-apply/review/{id}")
    public Result reviewCreatorVipApply(@PathVariable Long id,
                                     @RequestAttribute(value = "userId", required = false) Long reviewerId,
                                     @RequestParam Boolean approved,
                                     @RequestParam(required = false) String reviewReason) {
        if (reviewerId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(vipService.reviewCreatorVipApply(id, reviewerId,
                approved, reviewReason));
    }

       
                               
      
                             
                     
                       
                   
  
    @ApiLog("获取VIP订单")
    @GetMapping("/orders")
    public Result getUserVipOrders(@RequestParam(required = false) Long userId,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer size) {
        if (userId != null) {
            return Result.success(vipService.getUserVipOrders(userId, page, size));
        }
                                     
        return Result.success(vipService.getPendingVipApplies(page, size));
    }

       
                 
      
                         
                        
                             
                              
                   
  
    @ApiLog("授予VIP")
    @PostMapping("/grant")
    public Result grantVip(@RequestParam Long userId,
                         @RequestParam Integer days,
                         @RequestParam(required = false) String reason,
                         @RequestAttribute(value = "userId", required = false) Long operatorId) {
        if (operatorId == null) {
            return Result.error(401, "Unauthorized");
        }
        return Result.success(vipService.grantVip(userId, days, reason, operatorId));
    }

       
                     
      
                   
  
    @ApiLog("获取VIP价格配置")
    @GetMapping("/price-config")
    public Result getVipPriceConfig() {
        return Result.success(vipService.getVipPriceConfig());
    }

       
                   
      
                             
                   
  
    @ApiLog("获取VIP申请条件")
    @GetMapping("/creator-condition/{creatorId}")
    public Result checkCreatorVipCondition(@PathVariable Long creatorId) {
        return Result.success(vipService.checkCreatorVipCondition(creatorId));
    }
}
