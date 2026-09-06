package com.haoran.music.vo.favorite;

import lombok.Data;


@Data
public class FavoriteGroupItemVO {
    private Long groupId;
    private String resourceType;
    private Long resourceId;
}
