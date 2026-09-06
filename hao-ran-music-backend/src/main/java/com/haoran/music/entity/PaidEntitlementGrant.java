


package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;




@Data
@TableName("paid_entitlement_grant")
public class PaidEntitlementGrant {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paymentOrderId;
    private Long userId;
    private String resourceType;
    private Long resourceId;
    private Long paidResourceId;
    private String grantType;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private String status;
    private Long refundId;
    private LocalDateTime revokedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
