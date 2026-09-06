   
                           
  
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.NotificationBroadcastTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationBroadcastTaskMapper extends BaseMapper<NotificationBroadcastTask> {

    @Insert("INSERT INTO notification_broadcast_task (" +
            "task_id, operator_id, title, content, link, cover_url, cursor_user_id, total_users, " +
            "success_count, fail_count, batch_count, status, attempt_count, max_attempts, " +
            "deleted, create_time, update_time) VALUES (" +
            "#{taskId}, #{operatorId}, #{title}, #{content}, #{link}, #{coverUrl}, 0, #{totalUsers}, " +
            "0, 0, 0, #{status}, 0, #{maxAttempts}, 0, NOW(), NOW())")
    int insertTask(NotificationBroadcastTask task);

    @Select("SELECT * FROM notification_broadcast_task WHERE task_id = #{taskId} AND deleted = 0")
    NotificationBroadcastTask selectActiveByTaskId(@Param("taskId") String taskId);

    @Select("SELECT task_id FROM notification_broadcast_task " +
            "WHERE deleted = 0 AND attempt_count < max_attempts AND " +
            "((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) " +
            "OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW())) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<String> selectDueTaskIds(@Param("limit") int limit);

    @Update("UPDATE notification_broadcast_task SET status = 'failed', error_message = '执行节点中断，任务自动重新排队', next_retry_time = CASE WHEN attempt_count < max_attempts THEN NOW() ELSE NULL END, completed_at = NOW(), update_time = NOW() WHERE deleted = 0 AND status = 'running' AND started_at IS NOT NULL AND started_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE)")
    int recoverStaleRunningTasks();

    @Update("UPDATE notification_broadcast_task SET status = 'running', attempt_count = attempt_count + 1, started_at = NOW(), completed_at = NULL, error_message = NULL, update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND attempt_count < max_attempts AND ((status = 'pending' AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'failed' AND next_retry_time IS NOT NULL AND next_retry_time <= NOW()))")
    int claimTask(@Param("taskId") String taskId);

    @Update("UPDATE notification_broadcast_task SET status = 'failed', error_message = #{errorMessage}, next_retry_time = DATE_ADD(NOW(), INTERVAL 1 MINUTE), completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status IN ('pending', 'failed') AND attempt_count < max_attempts")
    int markSubmissionRejected(@Param("taskId") String taskId,
                               @Param("errorMessage") String errorMessage);

    @Update("UPDATE notification_broadcast_task SET cursor_user_id = #{cursorUserId}, success_count = #{successCount}, fail_count = #{failCount}, batch_count = #{batchCount}, update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running'")
    int updateProgress(@Param("taskId") String taskId,
                       @Param("cursorUserId") Long cursorUserId,
                       @Param("successCount") int successCount,
                       @Param("failCount") int failCount,
                       @Param("batchCount") int batchCount);

    @Update("UPDATE notification_broadcast_task SET status = 'success', error_message = NULL, next_retry_time = NULL, completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running'")
    int markSuccess(@Param("taskId") String taskId);

       
                                
       
    @Update("UPDATE notification_broadcast_task SET status = 'failed', error_message = #{errorMessage}, cursor_user_id = #{cursorUserId}, success_count = #{successCount}, fail_count = #{failCount}, batch_count = #{batchCount}, next_retry_time = CASE WHEN #{retryDelayMinutes} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{retryDelayMinutes} MINUTE) END, completed_at = NOW(), update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'running'")
    int markFailed(@Param("taskId") String taskId,
                   @Param("cursorUserId") Long cursorUserId,
                   @Param("successCount") int successCount,
                   @Param("failCount") int failCount,
                   @Param("batchCount") int batchCount,
                   @Param("errorMessage") String errorMessage,
                   @Param("retryDelayMinutes") Integer retryDelayMinutes);

    @Update("UPDATE notification_broadcast_task SET status = 'pending', operator_id = #{operatorId}, next_retry_time = NOW(), error_message = NULL, completed_at = NULL, update_time = NOW() WHERE task_id = #{taskId} AND deleted = 0 AND status = 'failed' AND attempt_count < max_attempts")
    int requestRetry(@Param("taskId") String taskId, @Param("operatorId") Long operatorId);

    @Select("SELECT status, COUNT(*) AS taskCount, COALESCE(SUM(total_users), 0) AS totalUsers, " +
            "COALESCE(SUM(success_count), 0) AS successCount, COALESCE(SUM(fail_count), 0) AS failCount " +
            "FROM notification_broadcast_task WHERE deleted = 0 GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();
}
