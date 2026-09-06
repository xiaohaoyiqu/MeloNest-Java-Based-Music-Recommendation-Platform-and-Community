package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_purchased")
public class UserPurchased extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private String resourceType;




    private Long resourceId;




    private String purchaseType;




    private Long purchaseOrderId;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime purchaseTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;
}
