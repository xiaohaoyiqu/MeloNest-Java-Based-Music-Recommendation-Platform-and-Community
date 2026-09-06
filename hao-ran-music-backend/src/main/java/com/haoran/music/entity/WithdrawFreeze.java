package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("withdraw_freeze")
public class WithdrawFreeze extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    @TableField("withdraw_apply_id")
    private Long withdrawId;




    @TableField("user_id")
    private Long creatorId;




    private Long refundId;




    private BigDecimal freezeAmount;




    private String status;




    private String reason;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;




    @TableLogic
    private Integer deleted;
}

