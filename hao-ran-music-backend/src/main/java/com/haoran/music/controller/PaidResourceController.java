   
                      
                                        
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.context.UserContext;
import com.haoran.music.common.exception.BusinessException;
import com.haoran.music.common.result.Result;
import com.haoran.music.common.result.ResultCode;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PaidResourceService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

   
                            
                                                                                            
   
@RestController
@RequestMapping("/paid-resource")
public class PaidResourceController {

    private final PaidResourceService paidResourceService;

    public PaidResourceController(PaidResourceService paidResourceService) {
        this.paidResourceService = paidResourceService;
    }

       
                                            
      
                                        
                                    
                                  
                                                 
                                    
                                        
                                            
  
    @ApiLog("Set paid resource")
    @PostMapping("/set")
    public Result<Map<String, Object>> setPaidResource(@RequestParam String resourceType,
                                                       @RequestParam Long resourceId,
                                                       @RequestParam BigDecimal price,
                                                       @RequestParam(required = false) Integer subscribePeriod,
                                                       @RequestParam(defaultValue = "create") String changeType,
                                                       @RequestParam(required = false) String changeReason) {
        Long ownerId = getRequiredUserId();
        Map<String, Object> result = paidResourceService.setPaidResource(ownerId, resourceType, resourceId,
                price, subscribePeriod, changeType, changeReason);
        return Result.success(result);
    }

       
                                      
      
                                                                
                                        
                                    
                               
  
    @ApiLog("Cancel paid resource")
    @DeleteMapping("/{id}")
    public Result<Boolean> cancelPaidResource(@PathVariable Long id,
                                              @RequestParam String resourceType,
                                              @RequestParam Long resourceId) {
        Long ownerId = getRequiredUserId();
        Boolean result = paidResourceService.cancelPaidResource(ownerId, resourceType, resourceId);
        return Result.success(result);
    }

       
                                          
      
                              
                            
                                 
  
    @ApiLog("Get my paid resources")
    @GetMapping("/my")
    public Result<Map<String, Object>> getMyPaidResources(@RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "20") Integer size) {
        Long ownerId = getRequiredUserId();
        Map<String, Object> result = paidResourceService.getMyPaidResources(ownerId, page, size);
        return Result.success(normalizePageResult(result, page, size));
    }

       
                                
      
                                        
                                    
                                   
  
    @ApiLog("Get paid resource detail")
    @GetMapping("/detail")
    public Result<Map<String, Object>> getPaidResourceDetail(@RequestParam String resourceType,
                                                            @RequestParam Long resourceId) {
        return Result.success(paidResourceService.getPaidResourceDetail(resourceType, resourceId));
    }

       
                                                           
      
                                        
                                    
                                
  
    @ApiLog("Check resource purchase")
    @GetMapping("/check")
    public Result<Boolean> checkPurchased(@RequestParam String resourceType,
                                          @RequestParam Long resourceId) {
        Long userId = getRequiredUserId();
        return Result.success(paidResourceService.checkPurchased(userId, resourceType, resourceId));
    }

       
                                         
      
                               
                             
                                       
                        
       
    @ApiLog("Buy paid resource")
    @PostMapping("/buy")
    public Result<Map<String, Object>> purchaseResource(@RequestParam String resourceType,
                                                        @RequestParam Long resourceId,
                                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Long userId = getRequiredUserId();
        return Result.success(paidResourceService.purchaseResource(
                userId, resourceType, resourceId, idempotencyKey));
    }

       
                                     
      
                                               
                              
                            
                                 
  
    @ApiLog("Get paid resource list")
    @GetMapping("/list")
    public Result<Map<String, Object>> getPaidResourceList(@RequestParam(required = false) String resourceType,
                                                           @RequestParam(defaultValue = "1") Integer page,
                                                           @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> result = paidResourceService.getPaidResourceList(resourceType, page, size);
        return Result.success(normalizePageResult(result, page, size));
    }

       
                                              
      
                                        
                                       
                                        
                            
  
    @ApiLog("Review paid resource")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{id}")
    public Result<Map<String, Object>> reviewPaidResource(@PathVariable Long id,
                                                          @RequestParam Boolean approved,
                                                          @RequestParam(required = false) String reviewReason) {
        Long reviewerId = getRequiredUserId();
        return Result.success(paidResourceService.reviewPaidResource(id, reviewerId, approved, reviewReason));
    }

       
                                  
      
                              
                            
                                         
  
    @ApiLog("Get pending paid resources")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping("/pending")
    public Result<Map<String, Object>> getPendingPaidResources(@RequestParam(defaultValue = "1") Integer page,
                                                               @RequestParam(defaultValue = "20") Integer size) {
        Map<String, Object> result = paidResourceService.getPendingPaidResources(page, size);
        return Result.success(normalizePageResult(result, page, size));
    }

       
                                              
      
                                               
                              
                            
                                      
  
    @ApiLog("Get purchased paid resources")
    @GetMapping("/purchased")
    public Result<Map<String, Object>> getUserPurchasedResources(@RequestParam(required = false) String resourceType,
                                                                 @RequestParam(defaultValue = "1") Integer page,
                                                                 @RequestParam(defaultValue = "20") Integer size) {
        Long userId = getRequiredUserId();
        Map<String, Object> result = paidResourceService.getUserPurchasedResources(userId, resourceType, page, size);
        return Result.success(normalizePageResult(result, page, size));
    }

       
                                                     
      
                                        
                                          
  
    @ApiLog("Get paid resource price range")
    @GetMapping("/price-range/{resourceType}")
    public Result<Map<String, BigDecimal>> getPriceRange(@PathVariable String resourceType) {
        Map<String, BigDecimal> serviceRange = paidResourceService.getPriceRange(resourceType);
        BigDecimal minPrice = serviceRange.get("min");
        BigDecimal maxPrice = serviceRange.get("max");
        BigDecimal recommendPrice = minPrice.add(maxPrice).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> result = new HashMap<>();
        result.put("minPrice", minPrice);
        result.put("maxPrice", maxPrice);
        result.put("avgPrice", recommendPrice);
        result.put("recommendPrice", recommendPrice);
        return Result.success(result);
    }

       
                                         
      
                              
  
    private Long getRequiredUserId() {
        Long userId = UserContext.getCurrentUserId();
        if (ObjectUtils.isEmpty(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Please login first");
        }
        return userId;
    }

       
                                                                                    
      
                                       
                              
                            
                                             
  
    private Map<String, Object> normalizePageResult(Map<String, Object> source, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>(source);
        Object records = result.containsKey("records") ? result.get("records") : result.get("list");
        int safePage = page == null || page < 1 ? 1 : page;
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        if (result.get("page") instanceof Number) {
            safePage = Math.max(1, ((Number) result.get("page")).intValue());
        }
        if (result.get("size") instanceof Number) {
            safeSize = Math.max(1, Math.min(((Number) result.get("size")).intValue(), 100));
        }

        result.put("records", records);
        result.put("current", safePage);
        result.put("size", safeSize);

        Object total = result.get("total");
        if (!result.containsKey("pages") && total instanceof Number) {
            long totalCount = ((Number) total).longValue();
            result.put("pages", (totalCount + safeSize - 1) / safeSize);
        }
        return result;
    }
}
