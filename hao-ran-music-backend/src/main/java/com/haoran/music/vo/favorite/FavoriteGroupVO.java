package com.haoran.music.vo.favorite;

import lombok.Data;

import java.time.LocalDateTime;

                
@Data
public class FavoriteGroupVO {
    private Long id;
    private String name;
    private Integer sortOrder;
    private Long itemCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
