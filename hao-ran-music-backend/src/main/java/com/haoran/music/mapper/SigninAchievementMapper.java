   
                      
                          
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SigninAchievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

   
             
   
@Mapper
public interface SigninAchievementMapper extends BaseMapper<SigninAchievement> {

       
                     
       
    @Select("SELECT * FROM signin_achievement WHERE days >= #{minDays} AND days <= #{maxDays} AND status = 1 AND deleted = 0 ORDER BY days ASC")
    List<SigninAchievement> selectByDaysRange(@Param("minDays") Integer minDays, @Param("maxDays") Integer maxDays);

       
                         
       
    @Select("SELECT * FROM signin_achievement WHERE status = 1 AND deleted = 0 ORDER BY days ASC")
    List<SigninAchievement> selectAllEnabled();
}
