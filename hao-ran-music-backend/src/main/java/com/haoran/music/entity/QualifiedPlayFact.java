package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

   
                   
  
                      
   
@Data
@TableName("qualified_play_fact")
public class QualifiedPlayFact {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long userId;
    private Long songId;
    private Integer progressSeconds;
    private Integer durationSeconds;
    private String policyVersion;
    private String factStatus;
    private String revokeReason;
    private LocalDateTime occurredAt;
    private LocalDateTime createTime;
}
