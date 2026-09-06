package com.haoran.music.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.haoran.music.common.dto.PageQuery;

import com.haoran.music.common.constant.CommonConstants;
   
                      
                     
   
public class PageUtils {

       
             
      
                          
                   
       
    public static <T> Page<T> buildPage(PageQuery query) {
        return buildPage(query.getPage(), query.getSize());
    }

       
             
      
                           
                       
                   
       
    public static <T> Page<T> buildPage(Integer page, Integer size) {
                     
        int pageNum = (page != null && page > 0) ? page : CommonConstants.DEFAULT_PAGE;
        int pageSize = (size != null && size > 0) ? size : CommonConstants.DEFAULT_SIZE;

                   
        if (pageSize > CommonConstants.MAX_SIZE) {
            pageSize = CommonConstants.MAX_SIZE;
        }

        return new Page<>(pageNum, pageSize);
    }

       
              
      
                       
                  
       
    public static long getPages(IPage<?> page) {
        return page.getPages();
    }

       
               
      
                       
                        
       
    public static boolean hasNext(IPage<?> page) {
        return page.getCurrent() < page.getPages();
    }

       
               
      
                       
                        
       
    public static boolean hasPrevious(IPage<?> page) {
        return page.getCurrent() > 1;
    }
}
