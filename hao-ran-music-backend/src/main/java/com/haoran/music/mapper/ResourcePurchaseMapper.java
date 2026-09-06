   
                      
                              
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.ResourcePurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

   
                 
   
@Mapper
public interface ResourcePurchaseMapper extends BaseMapper<ResourcePurchase> {

    @Select("SELECT "
            + "COALESCE(SUM(amount), 0) AS totalSales, "
            + "COALESCE(SUM(creator_earnings), 0) AS totalEarnings, "
            + "COALESCE(SUM(platform_fee), 0) AS totalPlatformFee, "
            + "COUNT(*) AS totalOrders "
            + "FROM order_resource "
            + "WHERE owner_id = #{ownerId} "
            + "AND owner_type = 'creator' "
            + "AND status = 'active'")
    Map<String, Object> selectCreatorEarnings(@Param("ownerId") Long ownerId);

    @Select("<script>"
            + "SELECT "
            + "COALESCE(SUM(amount), 0) AS totalSales, "
            + "COALESCE(SUM(platform_fee), 0) AS totalPlatformFee, "
            + "COUNT(*) AS totalOrders "
            + "FROM order_resource "
            + "WHERE owner_type = 'platform' "
            + "AND status = 'active' "
            + "<if test='startTime != null'>AND purchase_time &gt;= #{startTime} </if>"
            + "<if test='endTime != null'>AND purchase_time &lt; #{endTime} </if>"
            + "</script>")
    Map<String, Object> selectPlatformEarnings(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    @Select("SELECT "
            + "COALESCE(SUM(amount), 0) AS totalSales, "
            + "COALESCE(SUM(creator_earnings), 0) AS totalEarnings, "
            + "COUNT(*) AS totalOrders "
            + "FROM order_resource "
            + "WHERE resource_id = #{resourceId} "
            + "AND resource_type = #{resourceType} "
            + "AND status = 'active'")
    Map<String, Object> selectResourceSales(@Param("resourceId") Long resourceId,
                                            @Param("resourceType") String resourceType);
}
