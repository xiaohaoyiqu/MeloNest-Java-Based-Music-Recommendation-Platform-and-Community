package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("gift_order")
public class GiftOrder {




    @TableId(value = "id", type = IdType.AUTO)
    private Long id;




    private String giftNo;




    private String giftType;




    private Long giverId;




    private Long receiverId;




    private String targetType;




    private Long targetId;




    private String targetName;




    private Long sellerId;




    private String vipType;




    private Integer vipLevel;




    private Integer vipDays;




    private BigDecimal amount;




    private String currency;




    private String giftMessage;




    private Long paymentOrderId;




    private String status;




    private String completionError;




    private LocalDateTime completedAt;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;




    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;




    @TableLogic
    private Integer deleted;
}
