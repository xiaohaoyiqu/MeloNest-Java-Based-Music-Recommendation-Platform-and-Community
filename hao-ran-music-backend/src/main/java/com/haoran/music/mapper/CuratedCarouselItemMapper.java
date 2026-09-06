   
                      
   
package com.haoran.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.haoran.music.entity.CuratedCarouselItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CuratedCarouselItemMapper extends BaseMapper<CuratedCarouselItem> {

       
                                        
       
    @Select("<script>SELECT * FROM curated_carousel_item "
            + "WHERE deleted = #{deleted} "
            + "<if test='scene != null and scene != \"\"'>AND scene = #{scene} </if>"
            + "ORDER BY update_time DESC, create_time DESC</script>")
    List<CuratedCarouselItem> selectByDeleted(
            @Param("scene") String scene,
            @Param("deleted") int deleted);

       
                                      
       
    @Update("UPDATE curated_carousel_item "
            + "SET status = 0, operator_id = #{operatorId}, deleted = 1, update_time = NOW() "
            + "WHERE id = #{itemId} AND deleted = 0")
    int softRemove(@Param("itemId") Long itemId, @Param("operatorId") Long operatorId);

       
                                          
       
    @Update("UPDATE curated_carousel_item "
            + "SET status = CASE WHEN review_status = 1 THEN 1 ELSE 0 END, "
            + "operator_id = #{operatorId}, deleted = 0, update_time = NOW() "
            + "WHERE id = #{itemId} AND deleted = 1")
    int restore(@Param("itemId") Long itemId, @Param("operatorId") Long operatorId);
}
