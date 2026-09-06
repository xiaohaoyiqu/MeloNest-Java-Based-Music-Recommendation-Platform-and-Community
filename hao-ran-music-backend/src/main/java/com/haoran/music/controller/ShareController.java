package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.aspect.RateLimit;
import com.haoran.music.common.result.Result;
import com.haoran.music.service.ShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

   
                      
                     
   
@Slf4j
@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

       
             
      
                                                          
                             
                   
       
    @PostMapping("/generate")
    @ApiLog("生成分享链接")
    public Result<Map<String, Object>> generateShareLink(@RequestParam String type,
                                                        @RequestParam Long resourceId,
                                                        HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Map<String, Object> shareInfo = shareService.generateShareLink(type, resourceId, userId);
        return Result.success(shareInfo);
    }

       
                  
      
                           
                   
       
    @GetMapping("/resource/{shareCode}")
    @ApiLog("访问分享链接")
    public Result<Map<String, Object>> getResourceByShareCode(@PathVariable String shareCode,
                                                              HttpServletRequest request) {
        Map<String, Object> resourceInfo = shareService.getResourceByShareCode(shareCode);
        if (resourceInfo == null) {
            return Result.error(404, "分享链接不存在或已过期");
        }

                   
        Long userId = (Long) request.getAttribute("userId");
        shareService.incrementShareView(shareCode, userId);

        return Result.success(resourceInfo);
    }

       
             
      
                            
                             
                                              
                             
                 
       
    @PostMapping("/record")
    @ApiLog("记录分享行为")
    @RateLimit(maxRequests = 50, timeWindowSeconds = 3600, operation = "recordShare",
               message = "分享操作过于频繁，请稍后再试")
    public Result<Void> recordShare(@RequestParam String type,
                                    @RequestParam Long resourceId,
                                    @RequestParam String platform,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        shareService.recordShare(type, resourceId, platform, userId);
        return Result.success();
    }

       
             
      
                            
                             
                   
       
    @GetMapping("/stats")
    @ApiLog("获取分享统计")
    public Result<Map<String, Object>> getShareStats(@RequestParam String type,
                                                     @RequestParam Long resourceId) {
        Map<String, Object> stats = shareService.getShareStats(type, resourceId);
        return Result.success(stats);
    }

       
               
      
                         
                            
                     
       
    @PostMapping("/batch-generate")
    @ApiLog("批量生成分享链接")
    public Result<Map<String, Object>> batchGenerateShareLinks(@RequestBody Map<String, Object> items,
                                                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        Map<String, Object> result = shareService.batchGenerateShareLinks(items, userId);
        return Result.success(result);
    }

       
                   
      
                           
                              
                 
       
    @DeleteMapping("/{shareCode}")
    @ApiLog("取消分享")
    public Result<Void> cancelShare(@PathVariable String shareCode,
                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "请先登录");
        }

        boolean success = shareService.cancelShare(shareCode, userId);
        if (success) {
            return Result.success();
        }
        return Result.error(400, "取消失败或无权限操作");
    }
}
