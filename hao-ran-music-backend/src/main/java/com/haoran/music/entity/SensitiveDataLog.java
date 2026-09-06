package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("sensitive_data_log")
public class SensitiveDataLog implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private Long operatorId;




    private String operationType;




    private String dataType;




    private Long targetId;




    private String requestUri;




    private String ipAddress;




    private String userAgent;




    private String result;




    private String errorMessage;




    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
