   
                      
                      
   

package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

   
         
   
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("song_vote")
public class SongVote {

       
           
       
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

       
           
       
    private Long songId;

       
           
       
    private Long userId;

       
           
       
    private LocalDate voteDate;

       
           
       
    private Integer voteCount;

       
           
       
    private LocalDateTime createTime;
}
