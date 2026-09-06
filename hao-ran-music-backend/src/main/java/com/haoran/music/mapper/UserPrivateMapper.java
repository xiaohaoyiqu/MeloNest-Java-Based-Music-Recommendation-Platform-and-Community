




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserPrivate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;




@Mapper
public interface UserPrivateMapper extends BaseMapper<UserPrivate> {





    @Update("UPDATE user_sensitive_profile SET real_name = NULL, id_card = NULL, phone_encrypted = NULL, email_encrypted = NULL, province_code = NULL, city_code = NULL, district_code = NULL, address_detail = NULL, bank_name = NULL, bank_account = NULL, bank_account_name = NULL, security_question = NULL, security_answer = NULL, real_name_verified = 0, verify_time = NULL, verify_method = NULL, deleted = 1, update_time = NOW() WHERE user_id = #{userId} AND deleted = 0")
    int scrubAndDeleteSensitiveProfile(@Param("userId") Long userId);


    @Delete("DELETE FROM user_private WHERE user_id = #{userId}")
    int deletePrivacySettingsByUserId(@Param("userId") Long userId);
}
