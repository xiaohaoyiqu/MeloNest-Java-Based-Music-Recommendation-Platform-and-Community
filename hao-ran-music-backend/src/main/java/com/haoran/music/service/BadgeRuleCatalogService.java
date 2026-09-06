package com.haoran.music.service;

import com.haoran.music.entity.BadgeRule;

import java.time.LocalDateTime;
import java.util.List;

   
                 
  
                      
   
public interface BadgeRuleCatalogService {

       
                 
      
                            
                      
                      
       
    BadgeRule findActiveRule(String badgeType, LocalDateTime now);

       
                   
      
                      
                          
       
    List<BadgeRule> listActiveRules(LocalDateTime now);

       
                            
      
                            
                     
       
    BadgeRule resolveDisplayRule(Long ruleId);

       
                                 
      
                     
                         
       
    String resolveIcon(BadgeRule rule);
}
