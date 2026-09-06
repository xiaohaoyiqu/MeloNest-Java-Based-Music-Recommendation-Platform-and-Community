   
                      
                       
   

package com.haoran.music.service;

import com.haoran.music.entity.UserQuickPhrase;

import java.util.List;

   
          
   
public interface UserQuickPhraseService {

       
                       
      
                         
                    
       
    List<UserQuickPhrase> getUserPhrases(Long userId);

       
            
      
                         
                          
                     
       
    UserQuickPhrase addPhrase(Long userId, String phrase);

       
            
      
                            
                           
                           
                   
       
    boolean updatePhrase(Long phraseId, Long userId, String newPhrase);

       
            
      
                            
                           
                   
       
    boolean deletePhrase(Long phraseId, Long userId);

       
              
      
                            
                                     
                   
       
    boolean updateSortOrder(Long userId, List<Long> phraseIds);

       
                    
      
                          
                                    
       
    void replaceUserPhrases(Long userId, List<String> phrases);

       
                
      
                         
                   
       
    int clearUserPhrases(Long userId);
}
