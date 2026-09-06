   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserDecoration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

   
             
   
@Mapper
public interface UserDecorationMapper extends BaseMapper<UserDecoration> {
                                                             
    @Delete("DELETE FROM user_decoration WHERE user_id=#{userId} AND decoration_id=#{decorationId} " +
            "AND source='payment' AND deleted=0")
    int deletePaymentEntitlement(@Param("userId") Long userId,
                                 @Param("decorationId") String decorationId);
}
