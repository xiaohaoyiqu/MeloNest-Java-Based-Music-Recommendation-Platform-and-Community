package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;

   
                      
                     
   
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("artist")
public class Artist extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String originalName;
    private String nameEn;
    private String avatar;
    private String cover;
    private String description;
    private Integer gender;
    private LocalDate birthDate;
    private String location;
    private Integer type;
    private String genres;
    private String area;
    private String firstLetter;

       
          
       
    private Long fansCount;

       
          
       
    private Long songCount;

       
          
       
    private Long albumCount;

       
           
       
    private Long playCount;

       
          
       
    private Long commentCount;

       
                
       
    private Integer hotScore;

    private Integer status;
    @TableLogic
    private Integer deleted;
}
