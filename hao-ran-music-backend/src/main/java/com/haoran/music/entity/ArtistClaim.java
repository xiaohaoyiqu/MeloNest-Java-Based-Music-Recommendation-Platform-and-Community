


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("artist_claim")
public class ArtistClaim extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long artistId;
    private Long userId;
    private String status;
    private String sourceType;
    private Long reviewerId;
    @TableLogic
    private Integer deleted;
}
