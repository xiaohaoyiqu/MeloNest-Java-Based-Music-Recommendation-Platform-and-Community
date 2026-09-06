



package com.haoran.music.mapper;

import com.haoran.music.dto.store.StoreProductRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface StoreProductQueryMapper {

    String UNION_BODY =
            " SELECT 'emoji_package' AS product_type, id AS product_id, update_time"
                    + " FROM music_emoji_package"
                    + " WHERE is_deleted = 0"
                    + " AND (type <> 'custom' OR review_status = 'approved')"
                    + " AND (#{productType} IS NULL OR #{productType} = 'emoji_package')"
                    + " AND (#{keyword} = '' OR LOWER(name) LIKE CONCAT('%', #{keyword}, '%')"
                    + " OR CAST(id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))"
                    + " UNION ALL"
                    + " SELECT 'decoration' AS product_type, id AS product_id, update_time"
                    + " FROM decoration_config"
                    + " WHERE deleted = 0"
                    + " AND (source_type <> 'custom' OR review_status = 'approved')"
                    + " AND (#{productType} IS NULL OR #{productType} = 'decoration')"
                    + " AND (#{keyword} = '' OR LOWER(decoration_name) LIKE CONCAT('%', #{keyword}, '%')"
                    + " OR CAST(id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))";

    @Select("SELECT product_type, product_id FROM (" + UNION_BODY + ") products "
            + "ORDER BY update_time DESC, product_type, product_id DESC LIMIT #{offset}, #{size}")
    List<StoreProductRow> selectAdminPage(@Param("productType") String productType,
                                          @Param("keyword") String keyword,
                                          @Param("offset") long offset,
                                          @Param("size") int size);

    @Select("SELECT COUNT(*) FROM (" + UNION_BODY + ") products")
    long countAdminProducts(@Param("productType") String productType,
                            @Param("keyword") String keyword);
}
