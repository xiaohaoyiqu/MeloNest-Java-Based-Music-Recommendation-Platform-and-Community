   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.VipExchangeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface VipExchangeOrderMapper extends BaseMapper<VipExchangeOrder> {

    @Select("SELECT * FROM user_vip_exchange_order WHERE user_id = #{userId} AND request_id = #{requestId} LIMIT 1")
    VipExchangeOrder selectByUserAndRequestId(@Param("userId") Long userId,
                                              @Param("requestId") String requestId);

    @Update("UPDATE user_vip_exchange_order SET status = 'granted', before_points = #{beforePoints}, after_points = #{afterPoints}, granted_at = NOW(), update_time = NOW() WHERE id = #{id} AND user_id = #{userId} AND status = 'pending'")
    int markGranted(@Param("id") Long id,
                    @Param("userId") Long userId,
                    @Param("beforePoints") Integer beforePoints,
                    @Param("afterPoints") Integer afterPoints);
}
