



package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.DecorationPurchaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DecorationPurchaseRecordMapper extends BaseMapper<DecorationPurchaseRecord> {
    @Select("SELECT * FROM decoration_purchase_record WHERE payment_order_id=#{orderId} LIMIT 1 FOR UPDATE")
    DecorationPurchaseRecord selectByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Update("UPDATE decoration_purchase_record SET status='refunded', refund_id=#{refundId}, " +
            "refund_time=NOW(), update_time=NOW() WHERE payment_order_id=#{orderId} AND status='active'")
    int markRefunded(@Param("orderId") Long orderId, @Param("refundId") Long refundId);
}
