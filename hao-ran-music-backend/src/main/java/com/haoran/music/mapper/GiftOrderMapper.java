package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.GiftOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;





@Mapper
public interface GiftOrderMapper extends BaseMapper<GiftOrder> {

    @Select("SELECT * FROM gift_order WHERE payment_order_id = #{paymentOrderId} " +
            "AND deleted = 0 LIMIT 1 FOR UPDATE")
    GiftOrder selectByPaymentOrderForUpdate(@Param("paymentOrderId") Long paymentOrderId);
}
