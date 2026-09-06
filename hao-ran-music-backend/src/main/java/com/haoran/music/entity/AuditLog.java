   
                      
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("target_id")
    private Long recordId;

    @TableField("target_type")
    private String recordType;

    @TableField("operation_type")
    private String action;

    @TableField("old_value")
    private String oldStatus;

    @TableField("new_value")
    private String newStatus;

    private Long operatorId;

    private String operatorName;

    @TableField("remark")
    private String comment;

    @TableField("ip_address")
    private String operatorIp;

    private String userAgent;

    @TableField("create_time")
    private LocalDateTime operationTime;

    private Integer durationSeconds;

    private Boolean success;

    private String errorMessage;
}
