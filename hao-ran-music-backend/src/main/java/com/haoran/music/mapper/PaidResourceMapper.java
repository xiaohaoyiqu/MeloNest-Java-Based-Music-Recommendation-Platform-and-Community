   
                      
                              
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PaidResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

   
                 
   
@Mapper
public interface PaidResourceMapper extends BaseMapper<PaidResource> {

       
                                 
       
    @Select("SELECT * FROM music_paid_resource " +
            "WHERE resource_type = #{resourceType} AND resource_id = #{resourceId} LIMIT 1")
    PaidResource selectByResourceIdentity(@Param("resourceType") String resourceType,
                                          @Param("resourceId") Long resourceId);

    @Update("UPDATE music_paid_resource SET sales_count = COALESCE(sales_count, 0) + 1, total_earnings = COALESCE(total_earnings, 0) + #{amount} WHERE id = #{resourceId} AND is_enabled = 1 AND status = 'approved'")
    int incrementSalesAndEarnings(@Param("resourceId") Long resourceId,
                                  @Param("amount") BigDecimal amount);

       
                                                                                  
                                                                      
       
    @Update("UPDATE music_paid_resource SET sales_count = COALESCE(sales_count, 0) + 1, total_earnings = COALESCE(total_earnings, 0) + #{amount} WHERE id = #{resourceId}")
    int incrementHistoricalSalesAndEarnings(@Param("resourceId") Long resourceId,
                                            @Param("amount") BigDecimal amount);
}
