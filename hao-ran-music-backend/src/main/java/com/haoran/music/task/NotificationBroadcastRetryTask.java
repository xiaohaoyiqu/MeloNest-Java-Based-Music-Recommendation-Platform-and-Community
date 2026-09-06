   
                      
   
package com.haoran.music.task;

import com.haoran.music.service.NotificationBroadcastTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

   
                 
   
@Slf4j
@Component
public class NotificationBroadcastRetryTask {

    @Resource
    private NotificationBroadcastTaskService taskService;

    @Scheduled(cron = "${schedule.task.notification-broadcast-retry-cron:0 */5 * * * ?}")
    public void retryDueTasks() {
        try {
            int submitted = taskService.retryDueTasks(5);
            if (submitted > 0) {
                log.info("提交到期通知广播补偿任务: count={}", submitted);
            }
        } catch (Exception e) {
            log.warn("event=notification_broadcast_retry_scan_failed errorType={}",
                    e.getClass().getSimpleName());
        }
    }
}
