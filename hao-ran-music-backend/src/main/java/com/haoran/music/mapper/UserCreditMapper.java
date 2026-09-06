   
                      
                             
   

package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserCredit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

   
                
   
@Mapper
public interface UserCreditMapper extends BaseMapper<UserCredit> {

    @Select("SELECT " +
            "COALESCE(SUM(CASE WHEN credit_level = 'excellent' THEN 1 ELSE 0 END), 0) AS excellent, " +
            "COALESCE(SUM(CASE WHEN credit_level = 'good' THEN 1 ELSE 0 END), 0) AS good, " +
            "COALESCE(SUM(CASE WHEN credit_level = 'normal' THEN 1 ELSE 0 END), 0) AS normal, " +
            "COALESCE(SUM(CASE WHEN credit_level = 'poor' THEN 1 ELSE 0 END), 0) AS poor, " +
            "COALESCE(SUM(CASE WHEN credit_level = 'bad' THEN 1 ELSE 0 END), 0) AS bad, " +
            "COUNT(*) AS total " +
            "FROM user_credit")
    Map<String, Object> selectCreditStatistics();
}
