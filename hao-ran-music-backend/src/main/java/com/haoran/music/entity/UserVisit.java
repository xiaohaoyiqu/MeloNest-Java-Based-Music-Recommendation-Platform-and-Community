package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_visit")
public class UserVisit extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;




    @TableId(type = IdType.ASSIGN_ID)
    private Long id;




    private Long visitorId;




    private Long visitedUserId;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime visitTime;




    private String ipAddress;




    private String visitSource;
}
