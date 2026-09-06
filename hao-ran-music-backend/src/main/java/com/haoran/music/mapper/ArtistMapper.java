package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.Artist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

   
                      
                          
   
@Mapper
public interface ArtistMapper extends BaseMapper<Artist> {

       
               
      
                           
       
    @Update("UPDATE artist SET play_count = COALESCE(play_count, 0) + 1 WHERE id = #{artistId}")
    void incrementPlayCount(@Param("artistId") Long artistId);

    @Update("UPDATE artist SET comment_count = GREATEST(COALESCE(comment_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{artistId} AND deleted = 0")
    int adjustCommentCount(@Param("artistId") Long artistId, @Param("delta") int delta);

       
                         
      
                            
                       
                    
  
    @Update("UPDATE artist SET fans_count = GREATEST(COALESCE(fans_count, 0) + #{delta}, 0), update_time = NOW() WHERE id = #{artistId} AND deleted = 0 AND COALESCE(fans_count, 0) + #{delta} >= 0")
    int adjustFansCount(@Param("artistId") Long artistId, @Param("delta") int delta);
}
