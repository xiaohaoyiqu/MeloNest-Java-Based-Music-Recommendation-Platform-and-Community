   
                      
                            
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserSigninAchievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

   
               
   
@Mapper
public interface UserSigninAchievementMapper extends BaseMapper<UserSigninAchievement> {

       
                  
       
    @Select("SELECT * FROM user_signin_achievement WHERE user_id = #{userId} ORDER BY days ASC")
    List<UserSigninAchievement> selectByUserId(@Param("userId") Long userId);

       
                    
       
    @Select("SELECT * FROM user_signin_achievement WHERE user_id = #{userId} AND days = #{days}")
    UserSigninAchievement selectByUserIdAndDays(@Param("userId") Long userId, @Param("days") Integer days);

       
                                      
       
    @Select("SELECT * FROM user_signin_achievement WHERE user_id = #{userId} AND achievement_id = #{achievementId} LIMIT 1")
    UserSigninAchievement selectByUserIdAndAchievementId(@Param("userId") Long userId,
                                                         @Param("achievementId") Long achievementId);

       
                       
       
    @Select("SELECT COUNT(*) FROM user_signin_achievement WHERE user_id = #{userId} AND is_rewarded = 1")
    Integer countRewardedByUserId(@Param("userId") Long userId);

    @Update("UPDATE user_signin_achievement SET is_rewarded = 1, reward_time = NOW(), update_time = NOW() WHERE id = #{id} AND user_id = #{userId} AND achievement_id = #{achievementId} AND is_rewarded = 0")
    int markRewarded(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("achievementId") Long achievementId);
}
