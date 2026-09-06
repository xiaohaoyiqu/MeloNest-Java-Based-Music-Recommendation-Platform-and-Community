package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.LocalMusic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;





@Mapper
public interface LocalMusicMapper extends BaseMapper<LocalMusic> {








    int incrementPlayCount(@Param("userId") Long userId, @Param("id") Long id);









    int incrementPlayCountWithQuality(@Param("userId") Long userId, @Param("id") Long id,
                                      @Param("quality") Integer quality);










    int incrementPlayCountWithFileInfo(@Param("id") Long id, @Param("quality") Integer quality,
                                        @Param("duration") Integer duration, @Param("fileSize") Long fileSize);
}
