package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.common.constant.PublicStatsSql;
import com.haoran.music.entity.SongLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;





@Mapper
public interface SongLikeMapper extends BaseMapper<SongLike> {









    @Select("<script>"
            + "SELECT sl.user_id "
            + "FROM song_like sl "
            + "INNER JOIN `user` u ON u.id = sl.user_id "
            + "WHERE sl.is_favorite = 1 "
            + "AND sl.deleted = 0 "
            + "AND sl.user_id &lt;&gt; #{userId} "
            + "AND sl.song_id IN "
            + "<foreach collection='songIds' item='songId' open='(' separator=',' close=')'>"
            + "#{songId}"
            + "</foreach>"
            + PublicStatsSql.USER_FILTER_XML
            + " GROUP BY sl.user_id "
            + "ORDER BY COUNT(*) DESC, sl.user_id ASC "
            + "LIMIT #{limit}"
            + "</script>")
    List<Long> selectSimilarPublicUserIds(@Param("userId") Long userId,
                                          @Param("songIds") List<Long> songIds,
                                          @Param("limit") int limit);


    @Select("SELECT COUNT(*) FROM song_like sl "
            + PublicStatsSql.INNER_USER_JOIN + "sl.user_id "
            + "WHERE sl.is_favorite = 1 AND sl.deleted = 0"
            + PublicStatsSql.USER_FILTER)
    Long countPublicActiveSongFavorites();
}
