




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.PlaylistSubscribe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




@Mapper
public interface PlaylistSubscribeMapper extends BaseMapper<PlaylistSubscribe> {

    @Select("SELECT " +
            "COUNT(*) AS totalSubscribers, " +
            "COALESCE(SUM(CASE WHEN status = 'active' AND expire_time >= NOW() THEN 1 ELSE 0 END), 0) AS activeSubscribers, " +
            "COALESCE(SUM(CASE WHEN start_time >= #{recentSince} THEN price ELSE 0 END), 0) AS monthlyRevenue, " +
            "COALESCE(SUM(price), 0) AS totalRevenue, " +
            "COALESCE(SUM(CASE WHEN status = 'active' THEN price ELSE 0 END), 0) AS activeRevenue, " +
            "COALESCE(SUM(CASE WHEN status = 'active' AND auto_renew = 1 THEN 1 ELSE 0 END), 0) AS activeAutoRenewCount " +
            "FROM playlist_subscribe " +
            "WHERE playlist_id = #{playlistId} AND deleted = 0")
    Map<String, Object> selectSubscriptionStatistics(@Param("playlistId") Long playlistId,
                                                       @Param("recentSince") LocalDateTime recentSince);

    @Select("SELECT DATE_FORMAT(start_time, '%Y-%m-%d') AS growthDate, COUNT(*) AS subscriberCount " +
            "FROM playlist_subscribe " +
            "WHERE playlist_id = #{playlistId} " +
            "AND deleted = 0 " +
            "AND start_time >= #{startDate} " +
            "AND start_time < #{endDate} " +
            "GROUP BY DATE(start_time)")
    List<Map<String, Object>> selectSubscriberGrowth(@Param("playlistId") Long playlistId,
                                                      @Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate);
}
