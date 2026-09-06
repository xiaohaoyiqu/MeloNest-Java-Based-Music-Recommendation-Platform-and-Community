package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;





@Data
@TableName("user_vip")
public class UserVip extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;







    private Integer vipLevel;




    private Integer vipStatus;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipStartTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime vipExpireTime;






    private Integer autoRenew;




    private Integer renewCycle;






    private String paymentMethod;






    private Integer totalVipDays;




    private Long totalSpending;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime firstPurchaseTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastPurchaseTime;












    private String source;




    @TableLogic
    private Integer deleted;
}
