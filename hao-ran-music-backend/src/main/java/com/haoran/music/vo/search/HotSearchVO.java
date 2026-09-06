package com.haoran.music.vo.search;

import lombok.Data;

import java.io.Serializable;

   
                      
                      
   
@Data
public class HotSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

       
          
       
    private String keyword;

       
          
       
    private Long heat;

       
                       
       
    private String trend;

       
         
       
    private Integer rank;
}
