package com.haoran.music.common.util;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

   
          
                                  
  
                      
   
@Slf4j
public final class ConvertHelper {

    private ConvertHelper() {
                    
    }

       
                   
      
                         
                        
                         
                         
                   
       
    public static <E, V> V toVO(E entity, Class<V> clazz) {
        if (ObjectUtils.isEmpty(entity)) {
            return null;
        }
        return BeanUtil.copyProperties(entity, clazz);
    }

       
                   
      
                        
                           
                        
                        
                       
       
    public static <V, E> E toEntity(V vo, Class<E> clazz) {
        if (ObjectUtils.isEmpty(vo)) {
            return null;
        }
        return BeanUtil.copyProperties(vo, clazz);
    }

       
                         
      
                               
                          
                           
                           
                   
       
    public static <E, V> List<V> toVOList(List<E> entities, Class<V> clazz) {
        if (ObjectUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }

        List<V> result = new ArrayList<>(entities.size());
        for (E entity : entities) {
            V vo = toVO(entity, clazz);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

       
                    
      
                                   
                              
                              
                              
                   
       
    public static <E, V> List<V> toVOList(List<E> entities, Function<E, V> converter) {
        if (ObjectUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }

        List<V> result = new ArrayList<>(entities.size());
        for (E entity : entities) {
            V vo = converter.apply(entity);
            if (vo != null) {
                result.add(vo);
            }
        }
        return result;
    }

       
                       
      
                            
                       
                        
                        
                   
       
    public static <E, V> IPage<V> toVOPage(IPage<E> page, Class<V> clazz) {
        if (page == null || ObjectUtils.isEmpty(page.getRecords())) {
            Page<V> emptyPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        return page.convert(entity -> toVO(entity, clazz));
    }

       
                    
      
                                
                            
                            
                            
                   
       
    public static <E, V> IPage<V> toVOPage(IPage<E> page, Function<E, V> converter) {
        if (page == null || ObjectUtils.isEmpty(page.getRecords())) {
            long current = page != null ? page.getCurrent() : 1;
            long size = page != null ? page.getSize() : 10;
            long total = page != null ? page.getTotal() : 0;
            Page<V> emptyPage = new Page<>(current, size, total);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        return page.convert(converter);
    }

       
                                
      
                               
                                 
                                            
                                 
                               
                               
       
    public static <V, ID> void setFieldFromSet(List<V> voList,
                                                  Function<V, ID> idExtractor,
                                                  Set<ID> idSet,
                                                  BiConsumer<V, Boolean> setter) {
        if (ObjectUtils.isEmpty(voList) || ObjectUtils.isEmpty(idSet)) {
                                    
            if (ObjectUtils.isNotEmpty(voList)) {
                for (V vo : voList) {
                    setter.accept(vo, false);
                }
            }
            return;
        }

        for (V vo : voList) {
            ID id = idExtractor.apply(vo);
            setter.accept(vo, idSet.contains(id));
        }
    }

       
               
                    
      
                              
                            
                             
                       
       
    public static <V> List<V> cloneVOList(List<V> sourceList, Class<V> clazz) {
        if (ObjectUtils.isEmpty(sourceList)) {
            return Collections.emptyList();
        }

        List<V> result = new ArrayList<>(sourceList.size());
        for (V source : sourceList) {
            V cloned = BeanUtil.copyProperties(source, clazz);
            result.add(cloned);
        }
        return result;
    }

       
                   
      
                              
                                
                              
                              
                   
       
    public static <V, ID> List<ID> extractIds(List<V> voList, Function<V, ID> idExtractor) {
        if (ObjectUtils.isEmpty(voList)) {
            return Collections.emptyList();
        }

        return voList.stream()
                .map(idExtractor)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toList());
    }

       
                   
      
                              
                                
                              
                              
                   
       
    public static <V, ID> Set<ID> extractIdSet(List<V> voList, Function<V, ID> idExtractor) {
        if (ObjectUtils.isEmpty(voList)) {
            return Collections.emptySet();
        }

        return voList.stream()
                .map(idExtractor)
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toSet());
    }

       
              
      
                       
                        
                       
                        
                    
       
    public static <V> IPage<V> emptyPage(long page, long size, Class<V> clazz) {
        Page<V> emptyPage = new Page<>(page, size, 0);
        emptyPage.setRecords(Collections.emptyList());
        return emptyPage;
    }

       
               
      
                        
                       
                        
                       
                        
                   
       
    public static <V> IPage<V> listToPage(List<V> list, long page, long size, Class<V> clazz) {
        if (ObjectUtils.isEmpty(list)) {
            return emptyPage(page, size, clazz);
        }

        int total = list.size();
        int start = (int) ((page - 1) * size);
        int end = (int) Math.min(start + size, total);

        if (start >= total) {
            return emptyPage(page, size, clazz);
        }

        List<V> pageData = list.subList(start, end);
        Page<V> resultPage = new Page<>(page, size, total);
        resultPage.setRecords(pageData);
        return resultPage;
    }

       
                             
                            
      
                      
                       
       
    public static <T> String idsToSqlString(Set<T> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return "";
        }

        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

       
                             
      
                      
                       
       
    public static <T> String idsToSqlString(List<T> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return "";
        }

        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

       
              
                         
      
                              
                              
                     
       
    public static Integer toInteger(String value, Integer defaultValue) {
        if (ObjectUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

       
               
      
                               
                              
                      
       
    public static Long toLong(String value, Long defaultValue) {
        if (ObjectUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

       
               
      
                               
                              
                      
       
    public static Boolean toBoolean(String value, Boolean defaultValue) {
        if (ObjectUtils.isEmpty(value)) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value) || "1".equals(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value) || "0".equals(value)) {
            return false;
        }
        return defaultValue;
    }

       
                      
      
                              
                             
                              
                              
                              
                              
                   
       
    public static <E, V> IPage<V> toPage(List<E> list,
                                           long currentPage,
                                           long pageSize,
                                           Function<E, V> converter) {
        if (ObjectUtils.isEmpty(list)) {
            Page<V> emptyPage = new Page<>(currentPage, pageSize, 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        int total = list.size();
        int start = (int) ((currentPage - 1) * pageSize);
        int end = (int) Math.min(start + pageSize, total);

        if (start >= total) {
            Page<V> emptyPage = new Page<>(currentPage, pageSize, total);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        List<E> pageData = list.subList(start, end);
        List<V> voList = new ArrayList<>(pageData.size());

        for (E entity : pageData) {
            V vo = converter.apply(entity);
            if (vo != null) {
                voList.add(vo);
            }
        }

        Page<V> resultPage = new Page<>(currentPage, pageSize, total);
        resultPage.setRecords(voList);
        return resultPage;
    }
}
