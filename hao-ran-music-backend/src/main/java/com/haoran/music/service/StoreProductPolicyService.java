



package com.haoran.music.service;

import com.haoran.music.entity.StoreProductPolicy;
import com.haoran.music.vo.StoreProductVO;

import java.util.Map;
import java.util.List;

public interface StoreProductPolicyService {
    StoreProductPolicy getPolicy(String productType, Long productId);

    boolean isCatalogVisible(String productType, Long productId);

    boolean isDirectlyVisible(String productType, Long productId);

    boolean canUseOwnedEntitlement(String productType, Long productId);

    void requirePurchasable(String productType, Long productId);

    StoreProductPolicy changeByOwner(String productType, Long productId, Long ownerId,
                                     String action, String reason);

    StoreProductPolicy changeByAdmin(String productType, Long productId, Long operatorId,
                                     String action, String reason);

    Map<String, Object> getAdminProducts(String productType, String keyword,
                                         Integer page, Integer size);

    StoreProductVO getProduct(String productType, Long productId);


    List<StoreProductVO> getPublicSellerProducts(Long sellerId, Integer limit);
}
