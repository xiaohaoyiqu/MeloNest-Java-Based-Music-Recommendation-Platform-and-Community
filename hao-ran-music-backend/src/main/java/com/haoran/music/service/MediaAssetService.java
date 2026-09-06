package com.haoran.music.service;

   
               
  
                      
   
public interface MediaAssetService {

       
                                  
                                             
      
                          
                                  
                                
                               
                                     
                           
       
    void registerStagedSubmissionAsset(Long ownerId, String mediaType, String originalName,
                                       String publicUrl, String storagePath, Long fileSize);

       
                     
      
                            
                               
                             
                            
                             
                              
                              
                           
                               
                             
       
    void registerAndRetain(String mediaType, String sourceType, Long sourceId, String assetRole,
                           String publicUrl, String storageNode, String storagePath, Long fileSize,
                           String targetType, Long targetId);

       
                   
      
                            
                               
                             
                               
                                          
                           
                            
       
    void registerSubmissionAssets(String mediaType, String sourceType, Long sourceId,
                                  String primaryUrl, String additionalUrls, String zipUrl, Long fileSize);

       
                       
      
                             
                           
       
    void releaseTargetReferences(String targetType, Long targetId);

       
                         
      
                          
                    
       
    int reclaimOrphanAssets(int limit);
}
