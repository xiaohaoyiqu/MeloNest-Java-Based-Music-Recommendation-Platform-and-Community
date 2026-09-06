   
                      
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.StoreProductPolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StoreProductPolicyMapper extends BaseMapper<StoreProductPolicy> {

    @Select("SELECT * FROM store_product_policy WHERE product_type = #{productType} AND product_id = #{productId} LIMIT 1 FOR UPDATE")
    StoreProductPolicy selectForUpdate(@Param("productType") String productType,
                                       @Param("productId") Long productId);

    @Update("UPDATE store_product_policy SET sale_status=#{saleStatus}, visibility=#{visibility}, " +
            "entitlement_policy=#{entitlementPolicy}, settlement_status=#{settlementStatus}, " +
            "last_operator_id=#{operatorId}, reason=#{reason}, version=version+1, update_time=NOW() " +
            "WHERE id=#{id} AND version=#{version}")
    int updatePolicy(@Param("id") Long id,
                     @Param("version") Integer version,
                     @Param("saleStatus") String saleStatus,
                     @Param("visibility") String visibility,
                     @Param("entitlementPolicy") String entitlementPolicy,
                     @Param("settlementStatus") String settlementStatus,
                     @Param("operatorId") Long operatorId,
                     @Param("reason") String reason);
}
