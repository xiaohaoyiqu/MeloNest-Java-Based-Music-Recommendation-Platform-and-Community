


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserMusicSummaryBackfillTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;





@Mapper
public interface UserMusicSummaryBackfillTaskMapper extends BaseMapper<UserMusicSummaryBackfillTask> {

    @Insert("INSERT INTO user_music_summary_backfill_task (" +
            "task_id, business_key, operator_id, summary_type, start_period, end_period, cursor_period, " +
            "total_periods, completed_periods, affected_rows, status, attempt_count, max_attempts, " +
            "deleted, create_time, update_time) VALUES (" +
            "#{taskId}, #{businessKey}, #{operatorId}, #{summaryType}, #{startPeriod}, #{endPeriod}, " +
            "#{cursorPeriod}, #{totalPeriods}, 0, 0, #{status}, 0, #{maxAttempts}, 0, NOW(), NOW())")
    int insertTask(UserMusicSummaryBackfillTask task);

    @Select("SELECT * FROM user_music_summary_backfill_task WHERE task_id = #{taskId} AND deleted = 0")
    UserMusicSummaryBackfillTask selectActiveByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM user_music_summary_backfill_task WHERE business_key = #{businessKey} " +
            "AND deleted = 0 ORDER BY id DESC LIMIT 1")
    UserMusicSummaryBackfillTask selectByBusinessKey(@Param("businessKey") String businessKey);




    @Update("UPDATE user_music_summary_backfill_task SET status = 'pending', operator_id = #{operatorId}, cursor_period = start_period, completed_periods = 0, affected_rows = 0, failed_periods_json = NULL, attempt_count = 0, max_attempts = #{maxAttempts}, error_message = NULL, next_retry_time = NOW(), started_at = NULL, worker_id = NULL, lease_until = NULL, completed_at = NULL, update_time = NOW() WHERE business_key = #{businessKey} AND deleted = 0 AND status <> 'running'")
    int resetByBusinessKey(@Param("businessKey") String businessKey,
                           @Param("operatorId") Long operatorId,
                           @Param("maxAttempts") int maxAttempts);

    @Select("SELECT task_id FROM user_music_summary_backfill_task " +
            "WHERE deleted = 0 AND attempt_count < max_attempts AND " +
            "((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) " +
            "OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW())) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<String> selectDueTaskIds(@Param("limit") int limit);




    @Update("UPDATE user_music_summary_backfill_task SET status = 'failed', error_message = '执行节点中断，任务自动重新排队', next_retry_time = CASE WHEN attempt_count < max_attempts THEN NOW() ELSE NULL END, completed_at = NOW(), update_time = NOW(), worker_id = NULL, lease_until = NULL WHERE deleted = 0 AND status = 'running' AND (worker_id IS NULL OR lease_until IS NULL OR lease_until < NOW())")
    int recoverStaleRunningTasks();




    @Update("UPDATE user_music_summary_backfill_task SET status = 'running', attempt_count = attempt_count + 1, started_at = NOW(), worker_id = #{workerId}, lease_until = DATE_ADD(NOW(), INTERVAL 3 MINUTE), completed_at = NULL, error_message = NULL, update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND attempt_count < max_attempts AND ((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW()))")
    int claimTask(@Param("taskId") String taskId, @Param("workerId") String workerId);




    @Update("UPDATE user_music_summary_backfill_task SET lease_until = DATE_ADD(NOW(), INTERVAL 3 MINUTE), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running' AND worker_id = #{workerId} AND lease_until >= NOW()")
    int heartbeat(@Param("taskId") String taskId, @Param("workerId") String workerId);

    @Update("UPDATE user_music_summary_backfill_task SET status = 'failed', error_message = #{errorMessage}, next_retry_time = DATE_ADD(NOW(), INTERVAL 1 MINUTE), completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status IN ('pending', 'failed') AND attempt_count < max_attempts")
    int markSubmissionRejected(@Param("taskId") String taskId,
                               @Param("errorMessage") String errorMessage);




    @Update("UPDATE user_music_summary_backfill_task SET cursor_period = #{cursorPeriod}, completed_periods = #{completedPeriods}, affected_rows = #{affectedRows}, failed_periods_json = #{failedPeriodsJson}, lease_until = DATE_ADD(NOW(), INTERVAL 3 MINUTE), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running' AND worker_id = #{workerId} AND lease_until >= NOW()")
    int updateProgress(@Param("taskId") String taskId,
                       @Param("workerId") String workerId,
                       @Param("cursorPeriod") String cursorPeriod,
                       @Param("completedPeriods") int completedPeriods,
                       @Param("affectedRows") int affectedRows,
                       @Param("failedPeriodsJson") String failedPeriodsJson);




    @Update("UPDATE user_music_summary_backfill_task SET status = 'success', error_message = NULL, next_retry_time = NULL, worker_id = NULL, lease_until = NULL, completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running' AND worker_id = #{workerId} AND lease_until >= NOW()")
    int markSuccess(@Param("taskId") String taskId, @Param("workerId") String workerId);




    @Update("UPDATE user_music_summary_backfill_task SET status = 'failed', error_message = #{errorMessage}, next_retry_time = CASE WHEN #{retryDelayMinutes} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{retryDelayMinutes} MINUTE) END, worker_id = NULL, lease_until = NULL, completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running' AND worker_id = #{workerId} AND lease_until >= NOW()")
    int markFailed(@Param("taskId") String taskId,
                   @Param("workerId") String workerId,
                   @Param("errorMessage") String errorMessage,
                   @Param("retryDelayMinutes") Integer retryDelayMinutes);




    @Update("UPDATE user_music_summary_backfill_task SET status = 'failed', attempt_count = max_attempts, error_message = #{errorMessage}, next_retry_time = NULL, worker_id = NULL, lease_until = NULL, completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running' AND worker_id = #{workerId} AND lease_until >= NOW()")
    int markTerminalFailed(@Param("taskId") String taskId,
                           @Param("workerId") String workerId,
                           @Param("errorMessage") String errorMessage);

    @Update("UPDATE user_music_summary_backfill_task SET status = 'pending', operator_id = #{operatorId}, cursor_period = start_period, completed_periods = 0, affected_rows = 0, failed_periods_json = NULL, next_retry_time = NOW(), error_message = NULL, worker_id = NULL, lease_until = NULL, completed_at = NULL, update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'failed' AND attempt_count < max_attempts")
    int requestRetry(@Param("taskId") String taskId, @Param("operatorId") Long operatorId);

    @Select("SELECT status, COUNT(*) AS taskCount, COALESCE(SUM(completed_periods), 0) AS completedPeriods, " +
            "COALESCE(SUM(affected_rows), 0) AS affectedRows " +
            "FROM user_music_summary_backfill_task WHERE deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();
}
