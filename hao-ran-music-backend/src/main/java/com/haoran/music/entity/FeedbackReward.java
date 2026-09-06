




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("feedback_reward")
public class FeedbackReward extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long userId;




    private Long feedbackId;




    private String rewardLevel;




    private Integer points;




    private Integer vipDays;




    private Integer creditScore;




    private String rewardDescription;




    private String status;




    private Integer isGranted;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime grantedTime;




    private Long grantorId;




    private String cancelReason;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelledTime;




    private String remark;
}
