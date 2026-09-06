




package com.haoran.music.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;





public interface PaidResourceService {













    Map<String, Object> setPaidResource(Long ownerId, String resourceType, Long resourceId,
                                        BigDecimal price, Integer subscribePeriod,
                                        String changeType, String changeReason);









    Boolean cancelPaidResource(Long ownerId, String resourceType, Long resourceId);









    Map<String, Object> getMyPaidResources(Long ownerId, Integer page, Integer size);








    Map<String, Object> getPaidResourceDetail(String resourceType, Long resourceId);









    Boolean checkPurchased(Long userId, String resourceType, Long resourceId);










    Map<String, Object> purchaseResource(Long userId, String resourceType, Long resourceId,
                                         String idempotencyKey);










    Boolean grantPurchasedResource(Long userId, String resourceType, Long resourceId, Long orderId);






    Boolean grantPurchasedResource(Long userId, String resourceType, Long resourceId, Long orderId,
                                   Long paidResourceId, Integer subscribePeriod, BigDecimal saleAmount);









    Map<String, Object> getPaidResourceList(String resourceType, Integer page, Integer size);










    Map<String, Object> reviewPaidResource(Long resourceId, Long reviewerId,
                                          Boolean approved, String reviewReason);








    Map<String, Object> getPendingPaidResources(Integer page, Integer size);










    Map<String, Object> getUserPurchasedResources(Long userId, String resourceType,
                                                 Integer page, Integer size);








    Boolean isPriceInRange(String resourceType, BigDecimal price);







    Map<String, BigDecimal> getPriceRange(String resourceType);
}
