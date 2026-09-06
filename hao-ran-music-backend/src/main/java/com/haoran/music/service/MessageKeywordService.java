   
                      
                                     
  
          
                    
                
             
              
            
   

package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
              
                           
   
public interface MessageKeywordService {

       
                    
                         
      
                          
                         
                            
       
    List<KeywordExtractResult> extractMusicKeywords(String message, Long userId);

       
                      
      
                           
                         
                       
       
    Map<String, Integer> extractAndAggregateKeywords(List<String> messages, Long userId);

       
                
      
                         
                               
                          
                       
       
    List<Long> recommendByKeywords(Long userId, List<String> keywordList, int limit);

       
                    
      
                         
                   
       
    boolean isSocialRecommendEnabled(Long userId);

       
                 
      
                         
                          
       
    void setSocialRecommendEnabled(Long userId, boolean enabled);

       
              
             
      
                       
       
    void clearExpiredKeywords(int days);

       
                   
      
                         
                             
       
    void saveUserPreference(Long userId, SocialRecommendPreference preference);

       
                 
      
                         
       
    void clearUserKeywords(Long userId);

       
                          
      
                         
                        
                          
       
    Map<String, Object> comprehensiveSocialRecommend(Long userId, int limit);

       
                         
      
                         
                     
       
    SocialRecommendPreference getUserPreference(Long userId);

       
              
       
    class KeywordExtractResult {
           
                                         
           
        private String type;

           
                          
           
        private String hashedKeyword;

           
                        
           
        private Double confidence;

           
                
           
        private Long timestamp;

        public KeywordExtractResult() {}

        public KeywordExtractResult(String type, String hashedKeyword, Double confidence) {
            this.type = type;
            this.hashedKeyword = hashedKeyword;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getHashedKeyword() { return hashedKeyword; }
        public void setHashedKeyword(String hashedKeyword) { this.hashedKeyword = hashedKeyword; }

        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }

        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

       
               
       
    class SocialRecommendPreference {
           
                   
           
        private Boolean enabled;

           
                   
           
        private Boolean showSource;

           
                             
           
        private Boolean allowShared;

           
                  
           
        private Integer keywordRetentionDays;

        public SocialRecommendPreference() {
            this.enabled = false;
            this.showSource = true;
            this.allowShared = false;
            this.keywordRetentionDays = 7;
        }

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }

        public Boolean getShowSource() { return showSource; }
        public void setShowSource(Boolean showSource) { this.showSource = showSource; }

        public Boolean getAllowShared() { return allowShared; }
        public void setAllowShared(Boolean allowShared) { this.allowShared = allowShared; }

        public Integer getKeywordRetentionDays() { return keywordRetentionDays; }
        public void setKeywordRetentionDays(Integer keywordRetentionDays) { this.keywordRetentionDays = keywordRetentionDays; }
    }
}
