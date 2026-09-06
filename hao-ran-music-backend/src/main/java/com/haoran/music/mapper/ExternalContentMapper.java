package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.ExternalContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;




@Mapper
public interface ExternalContentMapper extends BaseMapper<ExternalContent> {

    @Select("SELECT * FROM external_content WHERE content_type = #{contentType} AND external_id = #{externalId} LIMIT 1")
    ExternalContent selectBySource(@Param("contentType") String contentType,
                                   @Param("externalId") String externalId);

    @Select("SELECT * FROM external_content WHERE status = 1 AND content_type = #{contentType} ORDER BY priority DESC, create_time DESC LIMIT #{limit}")
    List<ExternalContent> selectPublishedByType(@Param("contentType") String contentType, @Param("limit") int limit);

    @Select("SELECT * FROM external_content WHERE status = 1 ORDER BY priority DESC, create_time DESC LIMIT #{limit}")
    List<ExternalContent> selectAllPublished(@Param("limit") int limit);
}
