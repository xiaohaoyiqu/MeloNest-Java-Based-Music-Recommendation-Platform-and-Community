   
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

                    
@Data
@TableName("store_product_policy_log")
public class StoreProductPolicyLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long policyId;
    private String productType;
    private Long productId;
    private String actorType;
    private String action;
    private String beforeState;
    private String afterState;
    private Long operatorId;
    private String reason;
    private LocalDateTime createTime;
}
