package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@Data
@TableName("order_resource")
public class ResourcePurchase extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;



    private String orderNo;




    private Long userId;




    private Long resourceId;




    private String resourceType;




    private Long ownerId;



    private String ownerType;




    private BigDecimal amount;



    private BigDecimal platformFee;



    private BigDecimal creatorEarnings;



    private String status;




    private Long paymentOrderId;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime purchaseTime;



    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime refundTime;
}
