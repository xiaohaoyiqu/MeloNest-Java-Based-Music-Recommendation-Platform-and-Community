package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;





@Data
@TableName("reward_record")
public class RewardRecord extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private String orderNo;




    private Long userId;




    private Long creatorId;




    private Long resourceId;




    private String resourceType;




    private java.math.BigDecimal amount;




    private String message;




    private String status;




    private Long paymentOrderId;




    private Integer isAnonymous;
}
