package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;






@Data
@TableName("media_asset_reference")
public class MediaAssetReference implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long assetId;




    private String targetType;




    private Long targetId;




    private String referenceRole;




    private LocalDateTime createTime;




    private LocalDateTime releasedAt;
}
