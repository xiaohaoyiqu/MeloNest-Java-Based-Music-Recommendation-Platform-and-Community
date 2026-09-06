package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;





@Data
@TableName("order_vip")
public class VipOrder extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private String orderNo;




    private Long userId;




    private String vipType;




    private Integer days;




    private BigDecimal amount;




    private String status;

}
