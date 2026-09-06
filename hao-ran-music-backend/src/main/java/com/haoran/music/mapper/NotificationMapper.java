package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;





@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {







    @Select("SELECT id FROM `user` WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    Long lockRecipientForNotificationAggregation(@Param("userId") Long userId);

    @Select("SELECT type, COUNT(*) AS totalCount, " +
            "COALESCE(SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END), 0) AS unreadCount " +
            "FROM notification WHERE deleted = 0 OR deleted IS NULL GROUP BY type ORDER BY type")
    List<Map<String, Object>> selectAdminTypeStats();

    @Select("SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN is_read = 0 THEN 1 ELSE 0 END), 0) AS unread " +
            "FROM notification WHERE deleted = 0 OR deleted IS NULL")
    Map<String, Object> selectAdminTotals();
}
