package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;





@Data
@TableName("report")
public class Report extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long reporterId;




    private String targetType;




    private Long targetId;




    private String reportType;




    private String reason;




    private String description;




    private String attachmentUrls;




    private String status;




    private Long reviewerId;




    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime reviewTime;




    private String reviewResult;




    private String action;




    private Integer isRewarded;




    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
