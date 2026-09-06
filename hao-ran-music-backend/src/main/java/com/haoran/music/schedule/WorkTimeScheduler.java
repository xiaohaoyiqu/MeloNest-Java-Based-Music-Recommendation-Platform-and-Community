package com.haoran.music.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.haoran.music.common.config.WorkTimeConfig;
import com.haoran.music.common.constant.ModerationConstants;
import com.haoran.music.entity.ModerationRecord;
import com.haoran.music.entity.User;
import com.haoran.music.enums.UserRole;
import com.haoran.music.mapper.ModerationRecordMapper;
import com.haoran.music.mapper.UserMapper;
import com.haoran.music.service.ModerationAssignmentService;
import com.haoran.music.service.OnlineStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

   
                      
                        
   
@Slf4j
@Component
public class WorkTimeScheduler {

    private final WorkTimeConfig workTimeConfig;
    private final ModerationRecordMapper recordMapper;
    private final ModerationAssignmentService assignmentService;
    private final UserMapper userMapper;
    private final OnlineStatusService onlineStatusService;

    public WorkTimeScheduler(WorkTimeConfig workTimeConfig,
                             ModerationRecordMapper recordMapper,
                             ModerationAssignmentService assignmentService,
                             UserMapper userMapper,
                             OnlineStatusService onlineStatusService) {
        this.workTimeConfig = workTimeConfig;
        this.recordMapper = recordMapper;
        this.assignmentService = assignmentService;
        this.userMapper = userMapper;
        this.onlineStatusService = onlineStatusService;
    }

       
                   
               
       
    @Scheduled(cron = "${schedule.work-time.pending-tasks-cron}")
    public void checkPendingTasks() {
                   
        if (!workTimeConfig.isWorkTime()) {
            return;
        }

                  
        List<ModerationRecord> pendingTasks = recordMapper.selectList(
            new LambdaQueryWrapper<ModerationRecord>()
                .eq(ModerationRecord::getStatus, ModerationConstants.STATUS_PENDING)
                .isNull(ModerationRecord::getAssignedModeratorId)
                .orderByAsc(ModerationRecord::getPriority)
                .orderByAsc(ModerationRecord::getCreateTime)
                .last("LIMIT 50")
        );

        if (pendingTasks.isEmpty()) {
            return;
        }

        log.info("开始分配待处理任务，待分配数量: {}", pendingTasks.size());

        int successCount = 0;
        int failCount = 0;

        for (ModerationRecord task : pendingTasks) {
            try {
                if (!assignmentService.assignModeration(task.getId())) {
                    failCount++;
                    continue;
                }

                successCount++;
                log.debug("任务分配成功: recordId={}", task.getId());

            } catch (Exception e) {
                failCount++;
                log.error("event=work_time_task_assignment_failed recordId={} errorType={}",
                        task.getId(), e.getClass().getSimpleName());
            }
        }

        log.info("任务分配完成: 成功={}, 失败={}", successCount, failCount);
    }

       
                  
               
       
    @Scheduled(cron = "${schedule.work-time.moderator-online-refresh-cron}")
    public void refreshModeratorOnlineStatus() {
                   
        if (!workTimeConfig.isWorkTime()) {
            return;
        }

        onlineStatusService.refreshModeratorOnlineStatus();
    }

       
                
                
       
    @Scheduled(cron = "${schedule.work-time.online-status-cleanup-cron}")
    public void cleanExpiredOnlineStatus() {
        onlineStatusService.cleanExpiredOnlineStatus();
    }

       
                       
       
    @Scheduled(cron = "${schedule.work-time.daily-quota-reset-cron}")
    public void resetDailyQuota() {
                          
        userMapper.update(null,
            new LambdaUpdateWrapper<User>()
                .and(wrapper -> wrapper
                    .in(User::getRole, Arrays.asList(
                        UserRole.MODERATOR.getCode(),
                        UserRole.ADMIN.getCode(),
                        UserRole.SUPER_ADMIN.getCode()))
                    .or()
                    .eq(User::getIsModerator, 1))
                .set(User::getTodayReviewCount, 0)
        );

        log.info("重置审核员每日配额完成");
    }

       
                   
               
       
    @Scheduled(cron = "${schedule.work-time.ending-reminder-cron}")
    public void workTimeEndingReminder() {
        if (!workTimeConfig.isWorkTime()) {
            return;
        }

        Integer minutesUntilOff = workTimeConfig.getMinutesUntilOff(java.time.LocalTime.now());

                   
        if (minutesUntilOff != null && minutesUntilOff <= 15 && minutesUntilOff > 10) {
            log.info("工作时间即将结束，还有{}分钟", minutesUntilOff);
        }
    }
}
