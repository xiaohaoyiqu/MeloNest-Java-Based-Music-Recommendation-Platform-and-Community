



package com.haoran.music.service;

import com.haoran.music.entity.PaymentOrder;

public interface StoreEntitlementRefundService {
    boolean revoke(PaymentOrder order, Long refundId);
}
