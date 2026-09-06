package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserEmoji;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

   
                      
                              
   
@Mapper
public interface UserEmojiMapper extends BaseMapper<UserEmoji> {

    @Insert("INSERT INTO user_emoji "
            + "(user_id, emoji_package_id, is_purchased, is_favorited, purchase_time, create_time, update_time, deleted) "
            + "VALUES (#{userId}, #{packageId}, 1, 0, #{purchaseTime}, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE is_purchased = 1, purchase_time = #{purchaseTime}, "
            + "update_time = NOW(), deleted = 0")
    int grantPurchasedPackage(@Param("userId") Long userId,
                              @Param("packageId") Long packageId,
                              @Param("purchaseTime") LocalDateTime purchaseTime);

    @Update("UPDATE user_emoji SET is_purchased=0, purchase_time=NULL, update_time=NOW() " +
            "WHERE user_id=#{userId} AND emoji_package_id=#{packageId} AND is_purchased=1 AND deleted=0")
    int revokePurchasedPackage(@Param("userId") Long userId,
                               @Param("packageId") Long packageId);
}
