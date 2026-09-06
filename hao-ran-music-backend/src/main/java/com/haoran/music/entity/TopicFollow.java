




package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;




@Data
@EqualsAndHashCode(callSuper = false)
@TableName("topic_follow")
public class TopicFollow {




    @TableId(value = "id", type = IdType.AUTO)
    private Long id;




    private Long topicId;




    private Long userId;




    private LocalDateTime createTime;
}
