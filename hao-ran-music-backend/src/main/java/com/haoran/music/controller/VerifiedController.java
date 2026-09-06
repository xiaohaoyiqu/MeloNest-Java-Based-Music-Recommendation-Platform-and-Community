package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.dto.PageResult;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.VerifiedService;
import com.haoran.music.enums.UserRole;
import com.haoran.music.vo.user.PublicUserVO;
import com.haoran.music.vo.user.VerifiedInfoVO;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

   
                      
                              
   
@RestController
@RequestMapping("/verified")
public class VerifiedController {

    @Resource
    private VerifiedService verifiedService;

       
               
      
                         
                   
       
    @ApiLog("获取用户认证信息")
    @GetMapping("/user/{userId}")
    public Result<VerifiedInfoVO> getUserVerifiedInfo(@PathVariable Long userId) {
        VerifiedInfoVO result = verifiedService.getUserVerifiedInfo(userId);
        return Result.success(result);
    }

       
                     
      
                          
                          
                      
                        
                     
       
    @ApiLog("获取认证列表")
    @GetMapping("/list")
    public Result<PageResult<PublicUserVO>> getVerifiedList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        PageResult<PublicUserVO> result = verifiedService.getVerifiedList(type, level, page, size);
        return Result.success(result);
    }

       
                  
      
                                 
                                
                                
                                
                                
                   
       
    @ApiLog("审核认证申请")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PostMapping("/review/{creatorId}")
    public Result<Void> reviewVerified(
            @PathVariable Long creatorId,
            @RequestParam Boolean approved,
            @RequestParam String verifiedType,
            @RequestParam String verifiedLevel,
            @RequestParam String reason,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        verifiedService.reviewVerified(creatorId, approved, verifiedType, verifiedLevel, reason, operatorId);
        return Result.success();
    }

       
                    
      
                         
                         
                   
       
    @ApiLog("更新用户认证信息")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @PutMapping("/user/{userId}")
    public Result<Void> updateVerifiedInfo(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> params,
            @RequestAttribute(value = "userId", required = false) Long operatorId) {
        verifiedService.updateVerifiedInfo(userId, params, operatorId);
        return Result.success();
    }
}
