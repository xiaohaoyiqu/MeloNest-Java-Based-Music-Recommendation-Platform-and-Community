   
                      
   
package com.haoran.music.service;

import java.time.LocalDateTime;

public interface PaidEntitlementLedgerService {

    boolean recordGrant(Long paymentOrderId, Long userId, String resourceType, Long resourceId,
                        Long paidResourceId, String grantType,
                        LocalDateTime startsAt, LocalDateTime expiresAt);

    boolean revokeByRefund(Long paymentOrderId, Long refundId);
}
