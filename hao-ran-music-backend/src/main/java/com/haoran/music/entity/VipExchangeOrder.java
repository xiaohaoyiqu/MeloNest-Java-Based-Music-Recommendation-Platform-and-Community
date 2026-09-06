


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_vip_exchange_order")
public class VipExchangeOrder extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private Long packageId;
    private String packageCode;
    private Integer ruleVersion;
    private Integer vipLevel;
    private Integer vipDays;
    private Integer costPoints;
    private Integer beforePoints;
    private Integer afterPoints;
    private String status;
    private LocalDateTime grantedAt;
}
