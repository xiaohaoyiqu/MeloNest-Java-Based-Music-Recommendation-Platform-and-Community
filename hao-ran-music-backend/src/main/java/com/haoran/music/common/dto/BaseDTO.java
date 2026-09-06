package com.haoran.music.common.dto;

import com.haoran.music.common.util.ObjectUtils;
import javax.validation.constraints.Min;

   
                      
                          
   
public class BaseDTO {

       
           
       
    @Min(value = 1, message = "ID必须大于0")
    private Long id;

       
           
      
                 
       
    public Long getId() {
        return id;
    }

       
           
      
                   
       
    public void setId(Long id) {
        this.id = id;
    }

       
               
      
                                
       
    public boolean isIdEmpty() {
        return ObjectUtils.isEmpty(id);
    }

       
                
      
                                
       
    public boolean isIdNotEmpty() {
        return ObjectUtils.isNotEmpty(id);
    }
}
