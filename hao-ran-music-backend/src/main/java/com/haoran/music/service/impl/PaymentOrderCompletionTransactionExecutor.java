   
                      
   
package com.haoran.music.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.function.Supplier;

   
                                     
   
@Component
public class PaymentOrderCompletionTransactionExecutor {

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean executeBoolean(Supplier<Boolean> action) {
        boolean completed = Boolean.TRUE.equals(action.get());
        if (!completed) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return completed;
    }
}
