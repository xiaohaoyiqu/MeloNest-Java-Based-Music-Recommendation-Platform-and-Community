   
                      
                            
  
      
                       
                   
                  
   

package com.haoran.music.controller;

import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

   
            
   
@Slf4j
@RestController
@RequestMapping("/videos")
public class VideoTranscodeController {

    @Autowired(required = false)
    private com.haoran.music.service.impl.VideoPostServiceImpl videoPostService;

       
                
      
                                                                       
                  
       
    @GetMapping("/transcode/**")
    public ResponseEntity<Resource> transcodeVideo(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }

       
                    
      
                      
       
    @PostMapping("/cache/cleanup")
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    public Result<Integer> cleanupCache() {
        if (videoPostService != null) {
            int count = videoPostService.cleanupExpiredCache();
            return Result.success("清理完成，共清理" + count + "个文件", count);
        }
        return Result.error("视频服务不可用");
    }
}
