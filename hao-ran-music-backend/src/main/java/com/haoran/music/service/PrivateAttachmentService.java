package com.haoran.music.service;

import com.haoran.music.vo.attachment.PrivateAttachmentAssetVO;
import com.haoran.music.vo.attachment.PrivateAttachmentDownload;
import com.haoran.music.vo.attachment.PrivateAttachmentGrantVO;
import com.haoran.music.vo.attachment.PrivateAttachmentSessionVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

   
                     
  
                      
   
public interface PrivateAttachmentService {

       
              
      
                           
                          
                   
       
    PrivateAttachmentSessionVO createSession(Long ownerId, String purpose);

       
                   
      
                           
                               
                       
                   
       
    PrivateAttachmentAssetVO upload(Long ownerId, String sessionToken, MultipartFile file);

       
              
      
                           
                               
       
    void cancelSession(Long ownerId, String sessionToken);

       
                     
      
                           
                          
                           
                             
                           
       
    void bindAssets(Long ownerId, String purpose, List<Long> assetIds,
                    String targetType, Long targetId);

       
                    
      
                             
                           
                   
       
    List<Long> listTargetAssetIds(String targetType, Long targetId);

       
                
      
                          
                            
                   
       
    PrivateAttachmentGrantVO issueGrant(Long assetId, Long viewerId);

       
               
      
                          
                                
                           
                   
       
    PrivateAttachmentDownload loadForDownload(Long assetId, Long viewerId, String grant);

       
                
      
                             
                           
       
    void releaseTargetReferences(String targetType, Long targetId);

       
              
      
                         
                  
       
    int expireSessions(int limit);
}
