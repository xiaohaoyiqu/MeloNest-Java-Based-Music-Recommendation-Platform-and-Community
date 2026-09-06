package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("creator_work_purchase")
public class CreatorWorkPurchase implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private Long creatorId;




    private Long workId;




    private BigDecimal purchaseAmount;




    private String paymentMethod;




    private String orderNo;




    private String status;




    private LocalDateTime purchaseTime;




    private LocalDateTime expireTime;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
