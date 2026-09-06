package com.haoran.music.common.dto;

import com.haoran.music.common.constant.CommonConstants;
import lombok.Data;

import java.io.Serializable;

   
                      
                      
   
@Data

public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

       
               
       

    private Integer page = CommonConstants.DEFAULT_PAGE;

       
           
       

    private Integer size = CommonConstants.DEFAULT_SIZE;

       
           
       
    public PageQuery() {
    }

       
                    
       
    public PageQuery(Integer page, Integer size) {
        this.page = page;
        this.size = size;
    }

       
           
       

    private String sortField;

       
                     
       

    private String sortOrder;

                                 
    private String mainType;

                                  
    private String mainGenre;

                                
    private String language;

                                                       
    private String sortBy;

                                                  
    private Integer daysWithin;

                                                     
    private String keyword;

                                       
    private Integer minDuration;

                                       
    private Integer maxDuration;

                         
    private String paymentType;

                                                             
    private String quality;

       
                     
       
    public Integer getPage() {
        return page == null || page < 1 ? CommonConstants.DEFAULT_PAGE : page;
    }

       
                    
       
    public Integer getSize() {
        if (size == null || size < 1) {
            return CommonConstants.DEFAULT_SIZE;
        }
        return Math.min(size, CommonConstants.MAX_SIZE);
    }

       
                           
       
    public Integer getPageNum() {
        return getPage();
    }

       
                             
       
    public Integer getPageSize() {
        return getSize();
    }
}
