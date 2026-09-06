package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PaymentProofAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

   
                      
  
                      
   
@Mapper
public interface PaymentProofAssetMapper extends BaseMapper<PaymentProofAsset> {

    @Select("SELECT * FROM payment_proof_asset WHERE order_id = #{orderId} "
            + "AND status = 'active' LIMIT 1 FOR UPDATE")
    PaymentProofAsset selectActiveForUpdate(@Param("orderId") Long orderId);

    @Update("UPDATE payment_proof_asset SET status = 'cleanup_pending', next_retry_time = NOW(), worker_id = NULL, lease_until = NULL, error_category = NULL, update_time = NOW() WHERE order_id = #{orderId} AND proof_reference = #{proofReference} AND status = 'active'")
    int retireActive(@Param("orderId") Long orderId,
                     @Param("proofReference") String proofReference);

    @Select("SELECT id FROM payment_proof_asset WHERE attempt_count < max_attempts AND "
            + "((status IN ('cleanup_pending','failed') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW())) "
            + "OR (status = 'processing' AND lease_until IS NOT NULL AND lease_until <= NOW())) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<Long> selectDueIds(@Param("limit") int limit);

    @Update("UPDATE payment_proof_asset SET status = 'processing', worker_id = #{workerId}, lease_until = DATE_ADD(NOW(), INTERVAL #{leaseSeconds} SECOND), attempt_count = attempt_count + 1, error_category = NULL, update_time = NOW() WHERE id = #{id} AND attempt_count < max_attempts AND ((status IN ('cleanup_pending','failed') AND (next_retry_time IS NULL OR next_retry_time <= NOW())) OR (status = 'processing' AND lease_until IS NOT NULL AND lease_until <= NOW()))")
    int claim(@Param("id") Long id,
              @Param("workerId") String workerId,
              @Param("leaseSeconds") int leaseSeconds);

    @Select("SELECT * FROM payment_proof_asset WHERE id = #{id} AND status = 'processing' "
            + "AND worker_id = #{workerId} AND lease_until > NOW() LIMIT 1")
    PaymentProofAsset selectClaimed(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE payment_proof_asset SET status = 'cleaned', proof_reference = NULL, worker_id = NULL, lease_until = NULL, next_retry_time = NULL, error_category = NULL, cleaned_at = NOW(), update_time = NOW() WHERE id = #{id} AND status = 'processing' AND worker_id = #{workerId}")
    int markCleaned(@Param("id") Long id, @Param("workerId") String workerId);

    @Update("UPDATE payment_proof_asset SET status = 'failed', worker_id = NULL, lease_until = NULL, error_category = #{errorCategory}, next_retry_time = CASE WHEN #{retryDelaySeconds} IS NULL THEN NULL ELSE DATE_ADD(NOW(), INTERVAL #{retryDelaySeconds} SECOND) END, update_time = NOW() WHERE id = #{id} AND status = 'processing' AND worker_id = #{workerId}")
    int markFailed(@Param("id") Long id,
                   @Param("workerId") String workerId,
                   @Param("errorCategory") String errorCategory,
                   @Param("retryDelaySeconds") Integer retryDelaySeconds);

    @Update("UPDATE payment_proof_asset SET status = 'failed', attempt_count = max_attempts, worker_id = NULL, lease_until = NULL, error_category = #{errorCategory}, next_retry_time = NULL, update_time = NOW() WHERE id = #{id} AND status = 'processing' AND worker_id = #{workerId}")
    int markTerminalFailed(@Param("id") Long id,
                           @Param("workerId") String workerId,
                           @Param("errorCategory") String errorCategory);

    @Update("UPDATE payment_proof_asset SET status = 'cleanup_pending', attempt_count = 0, worker_id = NULL, lease_until = NULL, error_category = NULL, next_retry_time = NOW(), cleaned_at = NULL, update_time = NOW() WHERE id = #{id} AND status = 'failed' AND proof_reference IS NOT NULL")
    int requeueFailed(@Param("id") Long id);

    @Select("SELECT status, COUNT(*) AS assetCount, MIN(create_time) AS oldestCreateTime "
            + "FROM payment_proof_asset GROUP BY status ORDER BY status")
    List<Map<String, Object>> selectStatusSummary();

    @Select("SELECT id, order_id AS orderId, attempt_count AS attemptCount, "
            + "max_attempts AS maxAttempts, error_category AS errorCategory, "
            + "next_retry_time AS nextRetryTime, update_time AS updateTime "
            + "FROM payment_proof_asset WHERE status = 'failed' ORDER BY update_time DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentFailures(@Param("limit") int limit);
}
