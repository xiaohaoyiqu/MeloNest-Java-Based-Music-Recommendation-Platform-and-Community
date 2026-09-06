package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.SongResourceRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;

   
                      
                            
   
@Mapper
public interface SongResourceRequestMapper extends BaseMapper<SongResourceRequest> {

       
                 
       
    default int countTodayRequests(Long userId) {
        LocalDateTime startTime = LocalDate.now().atStartOfDay();
        return countRequestsInRange(userId, startTime, startTime.plusDays(1));
    }

    @Select("SELECT COUNT(*) FROM song_resource_request " +
            "WHERE user_id = #{userId} AND create_time >= #{startTime} AND create_time < #{endTime} AND deleted = 0")
    int countRequestsInRange(@Param("userId") Long userId,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);

       
                  
       
    @Select("SELECT COUNT(*) FROM song_resource_request WHERE user_id = #{userId} AND song_name = #{songName} AND artist_name = #{artistName} AND deleted = 0")
    int checkDuplicate(@Param("userId") Long userId, @Param("songName") String songName, @Param("artistName") String artistName);
}
