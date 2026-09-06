   
                      
                       
   

package com.haoran.music.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.haoran.music.common.dto.PageQuery;
import com.haoran.music.entity.Emoji;
import com.haoran.music.entity.EmojiPackage;
import com.haoran.music.vo.emoji.EmojiDisplayVO;

import java.util.List;
import java.util.Map;

   
          
   
public interface EmojiService {

       
                
      
                    
       
    List<EmojiPackage> getAllPackages(Long userId);

       
                 
      
                       
       
    List<EmojiPackage> getEnabledPackages(Long userId);

       
                
      
                             
                    
       
    EmojiPackage getPackageById(Long packageId, Long userId);

                                   
    EmojiPackage getPackageForReview(Long packageId);

       
                  
      
                             
                   
       
    List<Emoji> getEmojisByPackage(Long packageId, Long userId);

       
               
      
                         
                   
       
    List<Emoji> getEmojisByCategory(String category, Long userId);

       
               
      
                     
       
    List<Emoji> getSystemEmojis(Long userId);

       
           
      
                         
                   
       
    List<Emoji> searchEmojis(String keyword, Long userId);

                                       
    List<EmojiDisplayVO> resolveDisplayEmojis(List<String> codes);

                                      
    void requireContentEmojiAccess(String content, Long userId);

       
             
      
                          
       
    void recordUsage(Long emojiId, Long userId);

       
               
      
                              
                               
                    
       
    Long createCustomPackage(EmojiPackage emojiPackage, Long creatorId);

                                      
    void updateCustomPackage(Long packageId, EmojiPackage emojiPackage, Long creatorId);

       
              
      
                      
                   
       
    Long createCustomEmoji(Emoji emoji, Long creatorId);

       
                               
       
    List<Long> createCustomEmojis(List<Emoji> emojis, Long creatorId);

                                 
    void setCustomPackageCover(Long packageId, Long emojiId, Long creatorId);

                         
    void submitPackage(Long packageId, Long creatorId);

                          
    void reviewPackage(Long packageId, boolean approved, String reason, Long reviewerId);

       
              
      
                            
                             
                   
       
    boolean deleteCustomEmoji(Long emojiId, Long creatorId);

                               
    boolean deleteCustomPackage(Long packageId, Long creatorId);

       
              
      
                            
                      
       
    IPage<EmojiPackage> pagePackages(PageQuery pageQuery, Long userId);

       
                 
      
                             
                    
       
    List<EmojiPackage> getUserPackages(Long creatorId);

       
             
      
                        
                     
       
    List<Emoji> getHotEmojis(Integer limit, Long userId);

       
             
      
                   
       
    Map<String, Object> getEmojiStatistics();
}
