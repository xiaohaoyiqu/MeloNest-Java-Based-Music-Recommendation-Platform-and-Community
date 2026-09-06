package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.SongRating;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;





@Mapper
public interface SongRatingMapper extends BaseMapper<SongRating> {

    String PUBLIC_RATING_USER_JOIN = PublicStatsSql.INNER_USER_JOIN + "sr.user_id ";
    String PUBLIC_RATING_USER_FILTER = PublicStatsSql.USER_FILTER;







    @Select("SELECT COALESCE(AVG(sr.rating), 0) FROM song_rating sr"
            + PUBLIC_RATING_USER_JOIN
            + " WHERE sr.song_id = #{songId} AND sr.deleted = 0"
            + PUBLIC_RATING_USER_FILTER)
    Double getAvgRatingBySongId(@Param("songId") Long songId);







    @Select("SELECT COUNT(*) FROM song_rating sr"
            + PUBLIC_RATING_USER_JOIN
            + " WHERE sr.song_id = #{songId} AND sr.deleted = 0"
            + PUBLIC_RATING_USER_FILTER)
    Integer getRatingCountBySongId(@Param("songId") Long songId);







    @Select("SELECT sr.rating AS rating, COUNT(*) AS count FROM song_rating sr"
            + PUBLIC_RATING_USER_JOIN
            + " WHERE sr.song_id = #{songId} AND sr.deleted = 0"
            + PUBLIC_RATING_USER_FILTER
            + " GROUP BY sr.rating")
    List<Map<String, Object>> getRatingDistributionBySongId(@Param("songId") Long songId);








    @Select("SELECT rating FROM song_rating WHERE user_id = #{userId} AND song_id = #{songId} AND deleted = 0 LIMIT 1")
    Integer getUserRating(@Param("userId") Long userId, @Param("songId") Long songId);
}
