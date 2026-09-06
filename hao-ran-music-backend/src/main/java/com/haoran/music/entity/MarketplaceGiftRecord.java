package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                      
                            
   
@Data
@TableName("marketplace_gift_record")
public class MarketplaceGiftRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long giftOrderId;

    private Long paymentOrderId;

    private Long marketplaceItemId;

    private Long giverId;

    private Long receiverId;

    private Long sellerId;

       
                                  
       
    private String status;

    private LocalDateTime applyTime;

    private Long operatorId;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
