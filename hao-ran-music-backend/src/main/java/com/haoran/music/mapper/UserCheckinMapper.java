package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserCheckin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

   
                      
                            
   
@Mapper
public interface UserCheckinMapper extends BaseMapper<UserCheckin> {

       
                 
      
                         
                     
       
    @Select("SELECT IFNULL(MAX(continuous_days), 0) FROM user_checkin " +
            "WHERE user_id = #{userId} AND checkin_date >= #{startDate}")
    Integer getContinuousDays(@Param("userId") Long userId, @Param("startDate") LocalDate startDate);

       
                 
      
                         
                               
                   
       
    @Select("SELECT COUNT(*) FROM user_checkin " +
            "WHERE user_id = #{userId} AND checkin_date >= #{monthStart}")
    Integer getMonthCheckinCount(@Param("userId") Long userId, @Param("monthStart") LocalDate monthStart);
}
