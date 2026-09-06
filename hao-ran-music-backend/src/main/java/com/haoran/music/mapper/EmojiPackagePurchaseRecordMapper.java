



package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.EmojiPackagePurchaseRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmojiPackagePurchaseRecordMapper extends BaseMapper<EmojiPackagePurchaseRecord> {
    @Select("SELECT * FROM emoji_package_purchase_record WHERE payment_order_id=#{orderId} LIMIT 1 FOR UPDATE")
    EmojiPackagePurchaseRecord selectByOrderIdForUpdate(@Param("orderId") Long orderId);

    @Update("UPDATE emoji_package_purchase_record SET status='refunded', refund_id=#{refundId}, " +
            "refund_time=NOW(), update_time=NOW() WHERE payment_order_id=#{orderId} AND status='active'")
    int markRefunded(@Param("orderId") Long orderId, @Param("refundId") Long refundId);
}
