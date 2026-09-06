




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;





@Data
@EqualsAndHashCode(callSuper = false)
@TableName("marketplace_favorite")
public class MarketplaceFavorite {




    @TableId(value = "id", type = IdType.AUTO)
    private Long id;




    private Long itemId;




    private Long userId;




    private LocalDateTime createTime;





    @TableLogic
    private Integer deleted;




    private LocalDateTime updateTime;
}
