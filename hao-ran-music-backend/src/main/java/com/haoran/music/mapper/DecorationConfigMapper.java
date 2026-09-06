




package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.DecorationConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;




@Mapper
public interface DecorationConfigMapper extends BaseMapper<DecorationConfig> {

    @Select("SELECT * FROM decoration_config WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    DecorationConfig selectActiveByIdForUpdate(@Param("id") Long id);
}
