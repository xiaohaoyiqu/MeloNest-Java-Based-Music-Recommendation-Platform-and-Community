




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CreditRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




@Mapper
public interface CreditRecordMapper extends BaseMapper<CreditRecord> {









    @Select("SELECT COUNT(*) FROM credit_record " +
            "WHERE user_id = #{userId} " +
            "AND credit_type = #{creditType} " +
            "AND create_time >= #{startTime} " +
            "AND deleted = 0")
    Integer countTodayByType(@Param("userId") Long userId,
                              @Param("creditType") String creditType,
                              @Param("startTime") LocalDateTime startTime);









    @Select("SELECT COUNT(*) FROM credit_record " +
            "WHERE user_id = #{userId} " +
            "AND credit_type = #{creditType} " +
            "AND score < 0 " +
            "AND create_time >= DATE_SUB(NOW(), INTERVAL #{days} DAY) " +
            "AND deleted = 0")
    Integer countRecentNegativeByType(@Param("userId") Long userId,
                                       @Param("creditType") String creditType,
                                       @Param("days") Integer days);







    @Select("SELECT " +
            "credit_type, " +
            "COUNT(*) as count, " +
            "SUM(score) as total_score " +
            "FROM credit_record " +
            "WHERE user_id = #{userId} " +
            "AND deleted = 0 " +
            "GROUP BY credit_type")
    List<Map<String, Object>> getSummaryByUserId(@Param("userId") Long userId);
}
