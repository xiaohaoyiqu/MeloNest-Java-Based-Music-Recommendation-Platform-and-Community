   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.WithdrawApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

   
               
   
@Mapper
public interface WithdrawApplyMapper extends BaseMapper<WithdrawApply> {

    @Update("UPDATE withdraw_apply SET status = #{status}, reviewer_id = #{reviewerId}, review_time = #{reviewTime}, review_reason = #{reviewReason}, update_time = NOW() WHERE id = #{id} AND status = 'pending'")
    int reviewPending(WithdrawApply apply);

    @Update("UPDATE withdraw_apply SET status = 'completed', transaction_id = #{transactionId}, completed_time = #{completedTime}, update_time = NOW() WHERE id = #{id} AND status = 'processing'")
    int completeProcessing(WithdrawApply apply);
}
