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
@TableName("vip_gift_record")
public class VipGiftRecord {




    @TableId(value = "id", type = IdType.AUTO)
    private Long id;




    private Long giftOrderId;




    private Long paymentOrderId;




    private Long giverId;




    private Long receiverId;




    private String vipType;




    private Integer vipLevel;




    private Integer vipDays;




    private LocalDateTime startTime;




    private LocalDateTime endTime;




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
