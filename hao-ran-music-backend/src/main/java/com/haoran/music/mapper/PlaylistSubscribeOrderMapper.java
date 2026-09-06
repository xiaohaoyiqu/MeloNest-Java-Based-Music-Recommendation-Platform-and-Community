package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlaylistSubscribeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;





@Mapper
public interface PlaylistSubscribeOrderMapper extends BaseMapper<PlaylistSubscribeOrder> {

    @Select("SELECT * FROM playlist_subscribe_order " +
            "WHERE payment_order_id = #{paymentOrderId} AND deleted = 0 LIMIT 1")
    PlaylistSubscribeOrder selectByPaymentOrderId(@Param("paymentOrderId") Long paymentOrderId);

    @Update("UPDATE playlist_subscribe_order SET status = 'refunded', update_time = NOW() WHERE payment_order_id = #{paymentOrderId} AND status = 'paid' AND deleted = 0")
    int markPaidOrderRefunded(@Param("paymentOrderId") Long paymentOrderId);
}
