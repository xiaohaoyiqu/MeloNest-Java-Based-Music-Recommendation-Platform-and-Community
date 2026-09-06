package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("playlist_subscribe_order")
public class PlaylistSubscribeOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long playlistId;
    private Long creatorId;
    private String subscribeType;
    private Integer days;
    private BigDecimal amount;
    private Integer autoRenew;
    private String status;
    private Long paymentOrderId;
    private String idempotencyKey;
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
