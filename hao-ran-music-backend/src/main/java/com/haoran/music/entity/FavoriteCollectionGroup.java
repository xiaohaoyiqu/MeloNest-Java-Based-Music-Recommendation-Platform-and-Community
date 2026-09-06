package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@TableName("favorite_collection_group")
public class FavoriteCollectionGroup implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
