package com.haoran.music.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.haoran.music.entity.Lyric;

import java.util.List;
import java.util.Map;

   
                      
                      
   
public interface LyricService extends IService<Lyric> {

       
             
      
                         
                                    
                   
       
    Lyric getSongLyric(Long songId, Long userId);

       
                     
      
                            
                                          
                                            
                   
       
    Lyric getSongLyric(Long songId, String language, Integer lyricType);

       
           
      
                            
                                   
                            
                            
                            
                   
       
    Boolean saveLyric(Long songId, String content, String language, Integer lyricType, Long userId);

       
                 
      
                            
                                   
                            
                            
                                                               
                            
                   
       
    Boolean saveLyric(Long songId, String content, String language, Integer lyricType, String source, Long userId);

       
           
      
                          
                                
                   
       
    Boolean deleteLyric(Long lyricId, Long userId);

       
                  
      
                         
                   
       
    List<Lyric> getSongLyrics(Long songId, Long userId);

       
                     
      
                                 
                                 
                                            
                                 
                     
       
    Long requestTranslation(Long songId, String targetLanguage, Integer lyricType, Long userId);

       
                   
      
                           
                   
       
    Boolean executeTranslation(Long taskId);

       
               
      
                           
                           
                                                                             
       
    Map<String, Object> getTranslationStatus(Long taskId, Long userId);

       
                              
      
                         
                           
                                 
       
    Map<String, Object> getLatestTranslationStatus(Long songId, Long userId);

       
                
      
                   
       
    List<com.haoran.music.entity.MusicLanguage> getSupportedLanguages();

       
                   
      
                                    
       
    Map<String, Object> testDeepSeekConnection();

       
                     
      
                         
                   
       
    String getLocalLyricFromFile(Long songId);

       
                             
      
                             
                                  
                     
       
    Boolean syncLocalLyric(Long songId, Long operatorId);

       
                                  
      
                             
                                  
                                        
                                          
                     
       
    Boolean syncLocalLyric(Long songId, Long operatorId, Integer lyricType, String language);

       
                         
      
                                           
                                         
                                                
                                                        
                   
       
    Map<String, Object> calibrateNode3Lyrics(Long operatorId, Integer limit,
                                              Boolean dryRun, Boolean includeTranslations);
}
