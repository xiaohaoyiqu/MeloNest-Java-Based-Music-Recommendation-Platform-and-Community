package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("withdraw_apply")
public class WithdrawApply extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long creatorId;




    private BigDecimal amount;




    private String withdrawType;




    private String withdrawAccount;




    private String withdrawName;




    private String status;




    private Long reviewerId;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reviewTime;




    private String reviewReason;




    private String transactionId;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedTime;
}
