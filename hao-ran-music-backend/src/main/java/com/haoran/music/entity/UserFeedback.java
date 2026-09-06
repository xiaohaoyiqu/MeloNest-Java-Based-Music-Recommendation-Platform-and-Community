package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;





@Data
@TableName("user_feedback")
public class UserFeedback extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;





    private String feedbackType;




    private Long orderId;




    private String orderType;




    private String title;




    private String content;




    private String status;




    private Long handlerId;




    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime handleTime;




    private String handleResult;




    private String attachmentUrls;




    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}
