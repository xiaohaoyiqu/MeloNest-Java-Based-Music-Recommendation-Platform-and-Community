package com.haoran.music.task;

import com.haoran.music.service.SimpleUserClassificationService;
import com.haoran.music.service.UserMusicSummaryService;
import com.haoran.music.service.UserStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

   
                      
                          
   
@Slf4j
@Component
public class SimpleUserTask {

    @Autowired
    private SimpleUserClassificationService userClassificationService;

    @Autowired
    private UserStatisticsService userStatisticsService;

    @Autowired
    private UserMusicSummaryService userMusicSummaryService;

       
                    
       
    @Scheduled(cron = "${schedule.task.simple-user.daily-classification-cron}")
    public void dailyUserClassification() {
        log.info("========== 开始执行每日用户分类任务 ==========");
        try {
            LocalDate statDate = LocalDate.now().minusDays(1);
            userStatisticsService.dailyBatchCreateOrUpdate(statDate);
            userMusicSummaryService.refreshDailySummary(statDate);
            userMusicSummaryService.refreshMonthlySummary(YearMonth.from(statDate));
            userClassificationService.dailyUserClassification();
            log.info("========== 每日用户分类任务完成 ==========");
        } catch (Exception e) {
            log.error("event=bot_user_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }

       
                     
       
    @Scheduled(cron = "${schedule.task.simple-user.bot-check-cron}")
    public void checkBotUsers() {
        try {
            Integer updatedCount = userClassificationService.checkBotUsers();
            log.info("机器人用户检查完成: updatedCount={}", updatedCount);
        } catch (Exception e) {
            log.error("event=bot_user_check_failed errorType={}", e.getClass().getSimpleName());
        }
    }
}
