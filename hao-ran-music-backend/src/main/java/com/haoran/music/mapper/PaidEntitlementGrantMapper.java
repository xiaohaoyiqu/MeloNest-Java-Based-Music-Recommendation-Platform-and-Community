


package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PaidEntitlementGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaidEntitlementGrantMapper extends BaseMapper<PaidEntitlementGrant> {

    @Select("SELECT NOW()")
    LocalDateTime selectDatabaseTime();

    @Select("SELECT * FROM paid_entitlement_grant WHERE payment_order_id = #{paymentOrderId} LIMIT 1")
    PaidEntitlementGrant selectByPaymentOrderId(@Param("paymentOrderId") Long paymentOrderId);

    @Select("SELECT * FROM paid_entitlement_grant " +
            "WHERE user_id = #{userId} AND resource_type = #{resourceType} " +
            "AND resource_id = #{resourceId} AND status = 'active' " +
            "AND (expires_at IS NULL OR expires_at > NOW()) " +
            "ORDER BY (expires_at IS NULL) DESC, expires_at DESC, id DESC LIMIT 1")
    PaidEntitlementGrant selectEffectiveGrant(@Param("userId") Long userId,
                                              @Param("resourceType") String resourceType,
                                              @Param("resourceId") Long resourceId);

    @Select("SELECT * FROM paid_entitlement_grant " +
            "WHERE user_id = #{userId} AND resource_type = #{resourceType} " +
            "AND resource_id = #{resourceId} AND status = 'active' " +
            "ORDER BY starts_at ASC, id ASC")
    List<PaidEntitlementGrant> selectActiveGrants(@Param("userId") Long userId,
                                                  @Param("resourceType") String resourceType,
                                                  @Param("resourceId") Long resourceId);

    @Update("UPDATE paid_entitlement_grant SET status = 'revoked', refund_id = #{refundId}, revoked_at = NOW(), update_time = NOW() WHERE payment_order_id = #{paymentOrderId} AND status = 'active'")
    int revokeActiveByOrder(@Param("paymentOrderId") Long paymentOrderId,
                            @Param("refundId") Long refundId);

    @Update("UPDATE paid_entitlement_grant SET starts_at = #{newStartsAt}, expires_at = #{newExpiresAt}, update_time = NOW() WHERE id = #{id} AND status = 'active' AND starts_at = #{oldStartsAt} AND expires_at = #{oldExpiresAt}")
    int rebaseActiveGrant(@Param("id") Long id,
                          @Param("oldStartsAt") LocalDateTime oldStartsAt,
                          @Param("oldExpiresAt") LocalDateTime oldExpiresAt,
                          @Param("newStartsAt") LocalDateTime newStartsAt,
                          @Param("newExpiresAt") LocalDateTime newExpiresAt);
}
