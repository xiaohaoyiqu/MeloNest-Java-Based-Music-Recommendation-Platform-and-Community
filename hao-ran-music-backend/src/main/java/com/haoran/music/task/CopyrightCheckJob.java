package com.haoran.music.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.config.CopyrightCheckConfig;
import com.haoran.music.entity.CreatorWork;
import com.haoran.music.entity.Moderation;
import com.haoran.music.mapper.CreatorWorkMapper;
import com.haoran.music.mapper.ModerationMapper;
import com.haoran.music.service.ModerationIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

   
                      
                        
   
@Slf4j
@Component
public class CopyrightCheckJob {

    @Autowired
    private CreatorWorkMapper workMapper;

    @Autowired
    private ModerationMapper moderationMapper;

    @Autowired
    private ModerationIntegrationService moderationService;

    @Autowired
    private CopyrightCheckConfig copyrightCheckConfig;

       
                            
                       
       
    @Scheduled(cron = "${schedule.task.copyright.expiry-check-cron}")
    public void checkCopyrightExpiry() {
        log.info("开始检测版权即将到期内容...");

        try {
                          
            LocalDate warningDate = LocalDate.now().plusDays(copyrightCheckConfig.getExpiryWarningDays());
            List<CreatorWork> expiringWorks = workMapper.selectList(
                    new LambdaQueryWrapper<CreatorWork>()
                            .isNotNull(CreatorWork::getCopyrightExpireDate)
                            .le(CreatorWork::getCopyrightExpireDate, warningDate)
                            .eq(CreatorWork::getCopyrightStatus, 1)
            );

            log.info("检测到{}条即将到期的内容", expiringWorks.size());

            for (CreatorWork work : expiringWorks) {
                try {
                                
                    work.setCopyrightStatus(2);
                    workMapper.updateById(work);

                                      
                    Long existingReaudit = moderationMapper.selectCount(
                            new LambdaQueryWrapper<Moderation>()
                                    .eq(Moderation::getContentType, "creator_work")
                                    .eq(Moderation::getContentId, work.getId())
                                    .eq(Moderation::getReauditFromId, work.getId())
                                    .eq(Moderation::getStatus, 3)
                    );

                    if (existingReaudit == 0) {
                                              
                        Moderation reauditModeration = new Moderation();
                        reauditModeration.setContentType("creator_work");
                        reauditModeration.setContentId(work.getId());
                        reauditModeration.setTitle("版权即将到期复审: " + work.getWorkName());
                        reauditModeration.setDescription("作品版权将于" + work.getCopyrightExpireDate() + "到期");
                        reauditModeration.setSubmitterId(work.getUserId());
                        reauditModeration.setSubmitterName("系统自动");
                        reauditModeration.setStatus(3);        
                        reauditModeration.setReauditFromId(work.getId());
                        reauditModeration.setReviewReason("版权即将到期，需要确认续约或下架");
                        moderationMapper.insert(reauditModeration);

                        log.info("已创建版权到期复审记录: workId={}", work.getId());
                    }

                } catch (Exception e) {
                    log.error("event=copyright_expiry_warning_process_failed workId={} errorType={}",
                            work.getId(), e.getClass().getSimpleName());
                }
            }

            log.info("版权到期检测完成，共处理{}条", expiringWorks.size());

        } catch (Exception e) {
            log.error("event=copyright_expiry_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
                    
                       
       
    @Scheduled(cron = "${schedule.task.copyright.expired-content-cron}")
    public void handleExpiredContent() {
        log.info("开始处理已过期内容...");

        try {
            LocalDate today = LocalDate.now();
            List<CreatorWork> expiredWorks = workMapper.selectList(
                    new LambdaQueryWrapper<CreatorWork>()
                            .isNotNull(CreatorWork::getCopyrightExpireDate)
                            .lt(CreatorWork::getCopyrightExpireDate, today)
                            .ne(CreatorWork::getCopyrightStatus, 3)
            );

            log.info("检测到{}条已过期的内容", expiredWorks.size());

            for (CreatorWork work : expiredWorks) {
                try {
                           
                    work.setCopyrightStatus(3);        
                    work.setStatus(0);       
                    workMapper.updateById(work);

                             
                    Moderation expireModeration = new Moderation();
                    expireModeration.setContentType("creator_work");
                    expireModeration.setContentId(work.getId());
                    expireModeration.setTitle("版权已过期自动下架: " + work.getWorkName());
                    expireModeration.setDescription("作品版权已于" + work.getCopyrightExpireDate() + "过期");
                    expireModeration.setSubmitterId(work.getUserId());
                    expireModeration.setSubmitterName("系统自动");
                    expireModeration.setStatus(2);        
                    expireModeration.setReviewerId(0L);
                    expireModeration.setReviewerName("系统自动");
                    expireModeration.setReviewTime(java.time.LocalDateTime.now());
                    expireModeration.setReviewReason("版权已过期，自动下架。请联系管理员续约后重新提交审核。");
                    moderationMapper.insert(expireModeration);

                    log.info("已下架过期内容: workId={}, expireDate={}", work.getId(), work.getCopyrightExpireDate());

                } catch (Exception e) {
                    log.error("event=expired_content_unpublish_failed workId={} errorType={}",
                            work.getId(), e.getClass().getSimpleName());
                }
            }

            log.info("已过期内容处理完成，共处理{}条", expiredWorks.size());

        } catch (Exception e) {
            log.error("event=expired_content_process_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
                            
                        
       
    @Scheduled(cron = "${schedule.task.copyright.inactive-creators-cron}")
    public void checkInactiveCreators() {
        log.info("开始检查长期无作品的创作者...");

        try {
            log.info("inactive creator threshold days: {}", copyrightCheckConfig.getInactiveCreatorDays());
                                         
                           

            log.info("长期无作品创作者检查完成");

        } catch (Exception e) {
            log.error("event=inactive_creator_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
                    
       
    public void manualCheckCopyright() {
        log.info("手动触发版权检测...");
        checkCopyrightExpiry();
        handleExpiredContent();
    }
}
