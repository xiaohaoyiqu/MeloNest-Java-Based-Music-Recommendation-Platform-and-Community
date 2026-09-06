package com.haoran.music.controller;

import com.haoran.music.common.aspect.ApiLog;
import com.haoran.music.common.annotation.RequireRole;
import com.haoran.music.common.result.Result;
import com.haoran.music.entity.MV;
import com.haoran.music.enums.UserRole;
import com.haoran.music.service.MVService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

   
                      
                         
   
@Slf4j
@RestController
@RequestMapping("/admin/data-fix")
@RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
public class DataFixController {

    @Resource
    private MVService mvService;

       
                        
            
                                         
                                           
                                       
                                       
                                     
                                       
                                     
       
    @ApiLog("修复MV URL文件名")
    @PostMapping("/fix-mv-urls")
    public Result<MVFixResult> fixMVUrls() {
        log.info("[DataFix] 开始修复MV URL文件名");

        MVFixResult result = new MVFixResult();
        result.setTotalCount(0);
        result.setFixedCount(0);
        result.setSkippedCount(0);

        try {
                     
            List<MV> allMVs = mvService.list();
            result.setTotalCount(allMVs.size());

            log.info("[DataFix] 共查询到 {} 个MV", allMVs.size());

                     
            Pattern[] patterns = {
                    Pattern.compile("' t\\s"),                                
                    Pattern.compile("' re\\s"),                
                    Pattern.compile("' ll\\s"),                
                    Pattern.compile("' ve\\s"),                
                    Pattern.compile("' d\\s"),                
                    Pattern.compile("' s\\s"),                
                    Pattern.compile("' m\\s")                 
            };

            String[] replacements = {
                    "'t ",                
                    "'re ",                
                    "'ll ",                
                    "'ve ",                
                    "'d ",                
                    "'s ",                
                    "'m "                 
            };

            for (MV mv : allMVs) {
                boolean needUpdate = false;

                             
                if (mv.getUrl360p() != null && needsFix(mv.getUrl360p())) {
                    String fixed = fixUrl(mv.getUrl360p(), patterns, replacements);
                    if (!fixed.equals(mv.getUrl360p())) {
                        mv.setUrl360p(fixed);
                        needUpdate = true;
                        log.info("event=mv_url_repaired mvId={} quality=360p", mv.getId());
                    }
                }

                             
                if (mv.getUrl720p() != null && needsFix(mv.getUrl720p())) {
                    String fixed = fixUrl(mv.getUrl720p(), patterns, replacements);
                    if (!fixed.equals(mv.getUrl720p())) {
                        mv.setUrl720p(fixed);
                        needUpdate = true;
                        log.info("event=mv_url_repaired mvId={} quality=720p", mv.getId());
                    }
                }

                              
                if (mv.getUrl1080p() != null && needsFix(mv.getUrl1080p())) {
                    String fixed = fixUrl(mv.getUrl1080p(), patterns, replacements);
                    if (!fixed.equals(mv.getUrl1080p())) {
                        mv.setUrl1080p(fixed);
                        needUpdate = true;
                        log.info("event=mv_url_repaired mvId={} quality=1080p", mv.getId());
                    }
                }

                              
                if (needUpdate) {
                    mvService.updateById(mv);
                    result.setFixedCount(result.getFixedCount() + 1);
                    log.info("event=mv_url_repair_persisted mvId={}", mv.getId());
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            }

            log.info("[DataFix] 修复完成! 总数={}, 已修复={}, 跳过={}",
                    result.getTotalCount(), result.getFixedCount(), result.getSkippedCount());

            return Result.success(result);

        } catch (Exception e) {
            log.error("event=data_fix_execution_failed errorType={}", e.getClass().getSimpleName());
            result.setErrorMessage("修复过程失败，请查看服务端日志");
            return Result.error(500, "修复失败，请稍后重试");
        }
    }

       
                  
       
    private boolean needsFix(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
                        
        return url.contains("' t ") || url.contains("' re ") ||
                url.contains("' ll ") || url.contains("' ve ") ||
                url.contains("' d ") || url.contains("' s ") ||
                url.contains("' m ");
    }

       
                  
       
    private String fixUrl(String url, Pattern[] patterns, String[] replacements) {
        String result = url;

        for (int i = 0; i < patterns.length; i++) {
            Matcher matcher = patterns[i].matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(replacements[i]);
            }
        }

                           
        result = result.replaceAll("  ", " ");

        return result;
    }

       
           
       
    public static class MVFixResult {
        private Integer totalCount;         
        private Integer fixedCount;             
        private Integer skippedCount;          
        private String errorMessage;           

        public Integer getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }

        public Integer getFixedCount() {
            return fixedCount;
        }

        public void setFixedCount(Integer fixedCount) {
            this.fixedCount = fixedCount;
        }

        public Integer getSkippedCount() {
            return skippedCount;
        }

        public void setSkippedCount(Integer skippedCount) {
            this.skippedCount = skippedCount;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
