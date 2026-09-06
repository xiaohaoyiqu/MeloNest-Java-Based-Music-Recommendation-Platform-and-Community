package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;





@Data
@TableName("user_extension")
public class UserExtension implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;




    private Long userId;




    private Integer isCreator;




    private Integer creatorType;




    private Integer verified;




    private Integer followerCount;




    private Integer followingCount;




    private Integer worksCount;




    private Long totalPlays;




    private LocalDateTime createTime;




    private LocalDateTime updateTime;
}
