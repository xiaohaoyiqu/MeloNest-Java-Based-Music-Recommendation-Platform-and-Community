package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.UserWork;
import com.haoran.music.vo.user.UserWorkStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

   
                      
                          
   
@Mapper
public interface UserWorkMapper extends BaseMapper<UserWork> {

    @Update("UPDATE user_work SET status = #{status}, reviewer_id = #{reviewerId}, review_reason = #{reviewReason}, review_time = #{reviewTime}, update_time = NOW() WHERE id = #{id} AND status = 0 AND deleted = 0")
    int reviewPendingWork(UserWork work);

       
               
       
    @Select("SELECT * FROM user_work WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<UserWork> getUserWorks(@Param("userId") Long userId);

       
                      
       
    @Select("<script>" +
            "SELECT * FROM user_work " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "<if test='status != null'>AND status = #{status}</if> " +
            "ORDER BY create_time DESC" +
            "</script>")
    List<UserWork> getUserWorksByStatus(@Param("userId") Long userId, @Param("status") Integer status);

       
                     
       
    @Select("SELECT * FROM user_work WHERE status = 0 AND deleted = 0 ORDER BY create_time ASC")
    List<UserWork> getPendingWorks();

       
                          
       
    @Select("SELECT * FROM user_work WHERE status = 1 AND deleted = 0 ORDER BY publish_time DESC")
    List<UserWork> getPublishedWorks();

       
                 
       
    @Select("SELECT COUNT(*) FROM user_work WHERE user_id = #{userId} AND deleted = 0")
    Integer countByUserId(@Param("userId") Long userId);

       
             
       
    @Select("SELECT " +
            "COUNT(*) as totalCount, " +
            "SUM(CASE WHEN status = 0 THEN 1 ELSE 0 END) as pendingCount, " +
            "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as publishedCount, " +
            "SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as rejectedCount " +
            "FROM user_work WHERE user_id = #{userId} AND deleted = 0")
    UserWorkStats getWorkStats(@Param("userId") Long userId);
}
