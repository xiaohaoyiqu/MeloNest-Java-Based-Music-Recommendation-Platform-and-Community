package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.PushNotificationService;
import com.haoran.music.vo.push.PushNotificationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

   
                      
                       
   
@Slf4j
@RestController
@RequestMapping("/push-notifications")
public class PushNotificationController {

    @Autowired
    private PushNotificationService pushNotificationService;

       
                    
      
                     
       
    @GetMapping("/active")
    @ApiLog("获取推送通知")
    public Result<List<PushNotificationVO>> getActivePushNotifications() {
        List<PushNotificationVO> pushes = pushNotificationService.getActivePushNotifications();
        return Result.success(pushes);
    }

       
                  
      
                       
                     
       
    @GetMapping("/type/{type}")
    @ApiLog("获取指定类型推送")
    public Result<List<PushNotificationVO>> getPushNotificationsByType(@PathVariable String type) {
        List<PushNotificationVO> pushes = pushNotificationService.getPushNotificationsByType(type);
        return Result.success(pushes);
    }

       
                    
      
                         
                      
       
    @PostMapping("/create")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @ApiLog("创建推送通知")
    public Result<String> createPushNotification(@RequestBody PushNotificationVO push) {
        String pushId = pushNotificationService.createPushNotification(push);
        if (pushId != null) {
            return Result.successData(pushId);
        }
        return Result.error(500, "创建推送失败");
    }

       
                    
      
                         
                   
       
    @DeleteMapping("/{pushId}")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @ApiLog("删除推送通知")
    public Result<Void> deletePushNotification(@PathVariable String pushId) {
        boolean success = pushNotificationService.deletePushNotification(pushId);
        if (success) {
            return Result.success();
        }
        return Result.error(500, "删除推送失败");
    }
}
