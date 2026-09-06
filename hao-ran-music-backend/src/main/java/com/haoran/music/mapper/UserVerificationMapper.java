package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserVerification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

   
                      
                             
   
@Mapper
public interface UserVerificationMapper extends BaseMapper<UserVerification> {

       
              
       
    @Select("SELECT * FROM user_verification " +
            "WHERE target = #{target} " +
            "AND verification_type = #{type} " +
            "AND status = 0 " +
            "AND expire_time > #{now} " +
            "AND is_locked = 0 " +
            "AND deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT 1")
    UserVerification selectValidCode(@Param("target") String target,
                                      @Param("type") Integer type,
                                      @Param("now") LocalDateTime now);

                                                                 
    @Update("UPDATE user_verification SET status = 1, is_used = 1, used_time = #{usedTime} WHERE id = #{id} AND status = 0 AND is_used = 0 AND is_locked = 0 AND deleted = 0 AND expire_time > #{now} AND code_hash = #{codeHash}")
    int consumeValidCode(@Param("id") Long id,
                         @Param("codeHash") String codeHash,
                         @Param("now") LocalDateTime now,
                         @Param("usedTime") LocalDateTime usedTime);

                                                                                          
    @Update("UPDATE user_verification SET fail_count = COALESCE(fail_count, 0) + 1, is_locked = CASE WHEN COALESCE(fail_count, 0) + 1 >= #{maxFailCount} THEN 1 ELSE is_locked END WHERE id = #{id} AND status = 0 AND is_used = 0 AND is_locked = 0 AND deleted = 0 AND expire_time > #{now}")
    int recordFailedAttempt(@Param("id") Long id,
                            @Param("maxFailCount") Integer maxFailCount,
                            @Param("now") LocalDateTime now);

       
                         
       
    @Select("SELECT COUNT(*) FROM user_verification " +
            "WHERE target = #{target} " +
            "AND verification_type = #{type} " +
            "AND create_time >= #{startTime} " +
            "AND deleted = 0")
    Integer countRecentCodes(@Param("target") String target,
                              @Param("type") Integer type,
                              @Param("startTime") LocalDateTime startTime);

       
               
       
    @Select("SELECT * FROM user_verification " +
            "WHERE status = 0 " +
            "AND expire_time < #{now} " +
            "AND deleted = 0")
    List<UserVerification> selectExpiredCodes(@Param("now") LocalDateTime now);

       
                  
       
    @Select("SELECT * FROM user_verification " +
            "WHERE user_id = #{userId} " +
            "AND verification_type = #{type} " +
            "AND deleted = 0 " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit}")
    List<UserVerification> selectByUserAndType(@Param("userId") Long userId,
                                                 @Param("type") Integer type,
                                                 @Param("limit") Integer limit);
}
