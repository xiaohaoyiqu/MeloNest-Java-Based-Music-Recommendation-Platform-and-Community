



package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
@TableName("decoration_purchase_record")
public class DecorationPurchaseRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long decorationConfigId;
    private String decorationId;
    private Long paymentOrderId;
    private BigDecimal amount;
    private BigDecimal platformFeeRate;
    private BigDecimal platformFee;
    private BigDecimal creatorEarnings;
    private Integer contentVersion;
    private String currency;
    private String status;
    private Long refundId;
    private LocalDateTime refundTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
