




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;




@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    @Update("UPDATE refund_record SET status = #{targetStatus}, reviewer_id = #{reviewerId}, review_time = #{reviewTime}, review_reason = #{reviewReason}, is_unreasonable = #{isUnreasonable}, unreasonable_reason = #{unreasonableReason}, update_time = NOW() WHERE id = #{refundId} AND status = 'pending'")
    int reviewPending(@Param("refundId") Long refundId,
                      @Param("targetStatus") String targetStatus,
                      @Param("reviewerId") Long reviewerId,
                      @Param("reviewTime") LocalDateTime reviewTime,
                      @Param("reviewReason") String reviewReason,
                      @Param("isUnreasonable") Integer isUnreasonable,
                      @Param("unreasonableReason") String unreasonableReason);

    @Update("UPDATE refund_record SET status = 'completed', completed_time = #{completedTime}, update_time = NOW() WHERE id = #{refundId} AND status = 'approved'")
    int completeApproved(@Param("refundId") Long refundId,
                         @Param("completedTime") LocalDateTime completedTime);

    @Update("UPDATE refund_record SET status = 'cancelled', update_time = NOW() WHERE id = #{refundId} AND user_id = #{userId} AND status = 'pending'")
    int cancelPending(@Param("refundId") Long refundId, @Param("userId") Long userId);
}
