   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.util.PaymentOrderStatusUtil;
import com.haoran.music.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;
import java.util.List;

   
               
   
@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("SELECT * FROM payment_order WHERE id = #{orderId} AND deleted = 0 LIMIT 1 FOR UPDATE")
    PaymentOrder selectActiveByIdForUpdate(@Param("orderId") Long orderId);

    @Update("UPDATE payment_order SET status = #{targetStatus}, update_time = NOW() WHERE id = #{orderId} AND status = #{expectedStatus} AND deleted = 0")
    int transitionStatus(@Param("orderId") Long orderId,
                         @Param("expectedStatus") String expectedStatus,
                         @Param("targetStatus") String targetStatus);

    @Update("UPDATE payment_order SET status='cancelled', update_time=NOW() " +
            "WHERE business_type=#{businessType} AND business_id=#{businessId} " +
            "AND status='pending' AND deleted=0")
    int cancelPendingByProduct(@Param("businessType") String businessType,
                               @Param("businessId") Long businessId);

    @Select("SELECT id FROM payment_order " +
            "WHERE deleted = 0 AND status IN ('paid', 'success', 'completed') " +
            "AND (((completion_status IS NULL OR completion_status IN ('pending', 'blocked')) " +
            "AND COALESCE(completion_attempt_count, 0) < #{maxAttempts} " +
            "AND (completion_next_retry_time IS NULL OR completion_next_retry_time <= NOW())) " +
            "OR (completion_status = 'processing' AND completion_lease_until < NOW())) " +
            "ORDER BY id ASC LIMIT #{batchSize}")
    List<Long> selectRecoverableCompletionIds(@Param("batchSize") int batchSize,
                                              @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE payment_order SET completion_status = 'processing', completion_attempt_count = COALESCE(completion_attempt_count, 0) + 1, completion_last_attempt_time = NOW(), completion_next_retry_time = NULL, completion_lease_owner = #{workerId}, completion_lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW()), update_time = NOW() WHERE id = #{orderId} AND deleted = 0 AND status IN ('paid', 'success', 'completed') AND COALESCE(completion_attempt_count, 0) < #{maxAttempts} AND ((completion_status IS NULL OR completion_status IN ('pending', 'blocked')) AND (completion_next_retry_time IS NULL OR completion_next_retry_time <= NOW()) OR (completion_status = 'processing' AND completion_lease_until < NOW()))")
    int claimCompletion(@Param("orderId") Long orderId,
                        @Param("workerId") String workerId,
                        @Param("leaseSeconds") int leaseSeconds,
                        @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE payment_order SET completion_lease_owner = #{workerId}, completion_lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW()), update_time = NOW() WHERE id = #{orderId} AND deleted = 0 AND status IN ('paid', 'success', 'completed') AND completion_status = 'processing' AND completion_lease_until < NOW() AND COALESCE(completion_attempt_count, 0) >= #{maxAttempts}")
    int claimExhaustedCompletionForReconciliation(@Param("orderId") Long orderId,
                                                  @Param("workerId") String workerId,
                                                  @Param("leaseSeconds") int leaseSeconds,
                                                  @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE payment_order SET completion_status = 'processing', completion_last_attempt_time = NOW(), completion_next_retry_time = NULL, completion_lease_owner = #{workerId}, completion_lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW()), update_time = NOW() WHERE id = #{orderId} AND deleted = 0 AND status IN ('paid', 'success', 'completed') AND (completion_status IS NULL OR completion_status IN ('pending', 'blocked', 'failed') OR (completion_status = 'processing' AND completion_lease_until < NOW()))")
    int claimCompletionForManualRetry(@Param("orderId") Long orderId,
                                      @Param("workerId") String workerId,
                                      @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT * FROM payment_order WHERE id = #{orderId} AND deleted = 0 " +
            "AND completion_status = 'processing' AND completion_lease_owner = #{workerId} " +
            "AND completion_lease_until > NOW() LIMIT 1")
    PaymentOrder selectClaimedCompletion(@Param("orderId") Long orderId,
                                         @Param("workerId") String workerId);

    @Update("UPDATE payment_order SET completion_status = 'completed', completion_time = NOW(), completion_error = NULL, completion_next_retry_time = NULL, completion_lease_owner = NULL, completion_lease_until = NULL, completion_dead_letter_time = NULL, update_time = NOW() WHERE id = #{orderId} AND deleted = 0 AND completion_status = 'processing' AND completion_lease_owner = #{workerId}")
    int completeClaimedCompletion(@Param("orderId") Long orderId,
                                  @Param("workerId") String workerId);

    @Update("UPDATE payment_order SET completion_status = #{targetStatus}, completion_error = #{errorType}, completion_next_retry_time = CASE WHEN #{targetStatus} = 'failed' THEN NULL ELSE TIMESTAMPADD(SECOND, #{nextRetrySeconds}, NOW()) END, completion_dead_letter_time = CASE WHEN #{targetStatus} = 'failed' THEN NOW() ELSE NULL END, completion_lease_owner = NULL, completion_lease_until = NULL, update_time = NOW() WHERE id = #{orderId} AND deleted = 0 AND completion_status = 'processing' AND completion_lease_owner = #{workerId}")
    int releaseClaimedCompletion(@Param("orderId") Long orderId,
                                 @Param("workerId") String workerId,
                                 @Param("targetStatus") String targetStatus,
                                 @Param("errorType") String errorType,
                                 @Param("nextRetrySeconds") Long nextRetrySeconds);

    @Select("SELECT " +
            "SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending_count, " +
            "SUM(CASE WHEN status = 'submitted' THEN 1 ELSE 0 END) AS submitted_count, " +
            "SUM(CASE WHEN " + PaymentOrderStatusUtil.PAID_STATUS_SQL_FRAGMENT + " THEN 1 ELSE 0 END) AS paid_count, " +
            "SUM(CASE WHEN status = 'rejected' THEN 1 ELSE 0 END) AS rejected_count, " +
            "SUM(CASE WHEN status = 'cancelled' THEN 1 ELSE 0 END) AS cancelled_count, " +
            "SUM(CASE WHEN status = 'expired' THEN 1 ELSE 0 END) AS expired_count, " +
            "SUM(CASE WHEN status = 'refunding' THEN 1 ELSE 0 END) AS refunding_count, " +
            "SUM(CASE WHEN status = 'refunded' THEN 1 ELSE 0 END) AS refunded_count, " +
            "COALESCE(SUM(CASE WHEN " + PaymentOrderStatusUtil.PAID_STATUS_SQL_FRAGMENT + " THEN amount ELSE 0 END), 0) AS total_amount, " +
            "COALESCE(SUM(CASE WHEN " + PaymentOrderStatusUtil.PAID_STATUS_SQL_FRAGMENT + " AND review_time >= CURDATE() THEN amount ELSE 0 END), 0) AS today_amount " +
            "FROM payment_order WHERE deleted = 0")
    Map<String, Object> selectOrderStatistics();
}
