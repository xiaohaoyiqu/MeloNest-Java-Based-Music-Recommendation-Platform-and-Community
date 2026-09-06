package com.haoran.music.vo.user;

import lombok.Data;

import java.io.Serializable;

   
                      
                        
   
@Data
public class UserWorkStats implements Serializable {

    private static final long serialVersionUID = 1L;

       
           
       
    private Integer totalCount;

       
            
       
    private Integer pendingCount;

       
            
       
    private Integer publishedCount;

       
            
       
    private Integer rejectedCount;
}
