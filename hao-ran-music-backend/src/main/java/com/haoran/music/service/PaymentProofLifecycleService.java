package com.haoran.music.service;

import java.util.List;
import java.util.Map;

   
                    
  
                      
   
public interface PaymentProofLifecycleService {

    void recordReplacement(Long orderId, Long ownerId,
                           String previousReference, String newReference);

    boolean dispatchCleanup(Long assetId);

    int retryDueCleanup(int limit);

    boolean retryFailedCleanup(Long assetId);

    Map<String, Object> getStatusSummary();

    List<Map<String, Object>> getRecentFailures(int limit);
}
