package com.haoran.music.service;

import com.haoran.music.dto.gift.VipGiftCreateDTO;
import com.haoran.music.dto.gift.MarketplaceGiftCreateDTO;

import java.util.Map;





public interface VipGiftService {








    Map<String, Object> createVipGift(Long giverId, VipGiftCreateDTO dto);








    Map<String, Object> createMarketplaceGift(Long giverId, MarketplaceGiftCreateDTO dto);








    Map<String, Object> getGiftDetail(Long userId, Long giftOrderId);










    Map<String, Object> getSentGifts(Long userId, String status, Integer page, Integer size);










    Map<String, Object> getReceivedGifts(Long userId, String status, Integer page, Integer size);









    Map<String, Object> getAdminGiftOrders(String status, Integer page, Integer size);







    boolean completeVipGiftByPaymentOrder(Long paymentOrderId);







    boolean completeMarketplaceGiftByPaymentOrder(Long paymentOrderId);








    boolean retryVipGift(Long giftOrderId, Long operatorId);








    boolean retryGift(Long giftOrderId, Long operatorId);







    boolean hasAppliedVipGiftByPaymentOrder(Long paymentOrderId);







    boolean hasAppliedMarketplaceGiftByPaymentOrder(Long paymentOrderId);
}
