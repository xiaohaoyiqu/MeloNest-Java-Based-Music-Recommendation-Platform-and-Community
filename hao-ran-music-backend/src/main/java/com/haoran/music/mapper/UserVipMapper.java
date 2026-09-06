package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserVip;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;





@Mapper
public interface UserVipMapper extends BaseMapper<UserVip> {




    @Select("SELECT * FROM user_vip WHERE user_id = #{userId} " +
            "AND vip_status = 1 AND vip_expire_time > #{now} " +
            "AND deleted = 0 ORDER BY vip_expire_time DESC LIMIT 1")
    UserVip selectActiveVip(@Param("userId") Long userId, @Param("now") LocalDateTime now);




    @Select("SELECT * FROM user_vip WHERE user_id = #{userId} " +
            "AND deleted = 0 ORDER BY id DESC LIMIT 1 FOR UPDATE")
    UserVip selectLatestVipForUpdate(@Param("userId") Long userId);




    @Select("SELECT * FROM user_vip WHERE id > #{lastId} " +
            "AND vip_status = 1 AND auto_renew = 1 " +
            "AND vip_expire_time > #{startTime} AND vip_expire_time < #{expireTime} " +
            "AND deleted = 0 ORDER BY id ASC LIMIT #{limit}")
    List<UserVip> selectAutoRenewCandidatesAfter(@Param("lastId") Long lastId,
                                                   @Param("startTime") LocalDateTime startTime,
                                                   @Param("expireTime") LocalDateTime expireTime,
                                                   @Param("limit") int limit);




    @Select("SELECT * FROM user_vip WHERE vip_status = 1 " +
            "AND vip_expire_time BETWEEN #{now} AND #{expireTime} " +
            "AND auto_renew = 0 AND deleted = 0")
    List<UserVip> selectExpiringVips(@Param("now") LocalDateTime now,
                                      @Param("expireTime") LocalDateTime expireTime);




    @Select("SELECT * FROM user_vip WHERE vip_status = 1 " +
            "AND vip_expire_time BETWEEN #{now} AND #{expireTime} AND deleted = 0")
    List<UserVip> selectExpiringVipsForReminder(@Param("now") LocalDateTime now,
                                                 @Param("expireTime") LocalDateTime expireTime);




    @Select("SELECT * FROM user_vip WHERE vip_status = 1 " +
            "AND vip_expire_time < #{now} AND deleted = 0")
    List<UserVip> selectExpiredVips(@Param("now") LocalDateTime now);




    @Select("SELECT * FROM user_vip WHERE vip_level = 4 " +
            "AND vip_status = 1 AND deleted = 0")
    List<UserVip> selectLifetimeVips();




    @Update("UPDATE user_vip SET vip_status = 2 WHERE vip_status = 1 AND vip_expire_time < #{now} AND deleted = 0")
    int markExpiredVips(@Param("now") LocalDateTime now);
}
