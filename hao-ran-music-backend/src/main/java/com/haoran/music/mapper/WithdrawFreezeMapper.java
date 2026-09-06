   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.WithdrawFreeze;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

   
               
   
@Mapper
public interface WithdrawFreezeMapper extends BaseMapper<WithdrawFreeze> {

    @Update("UPDATE withdraw_freeze SET status = #{targetStatus}, handle_time = NOW(), update_time = NOW() WHERE id = #{freezeId} AND status = 'frozen' AND deleted = 0")
    int transitionFrozen(@Param("freezeId") Long freezeId,
                         @Param("targetStatus") String targetStatus);
}
