package com.haoran.music.service.search;

import java.util.Map;

   
                                        
  
                      
   
public interface SearchIndexService {

    Map<String, Object> rebuildAll();

    boolean isAvailable();

                                         
    long documentCount();

       
                                          
       
    void sync(String type, Long id);

                                            
    void applyOutboxSync(String type, Long id);
}
