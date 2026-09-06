package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@TableName("favorite_collection_item")
public class FavoriteCollectionItem implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long groupId;
    private String resourceType;
    private Long resourceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
