   
                      
   
package com.haoran.music.service.impl;

import com.alibaba.fastjson2.JSON;
import lombok.Data;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

   
                     
   
@Data
public class PaymentProductSnapshot {

    public static final int CURRENT_VERSION = 1;

    private Integer version;
    private String productCode;
    private String productName;
    private String businessType;
    private Long businessId;
    private BigDecimal amount;
    private String currency;
    private Long payeeId;
    private Map<String, Object> entitlement = new LinkedHashMap<>();

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public static PaymentProductSnapshot fromJson(String json) {
        return JSON.parseObject(json, PaymentProductSnapshot.class);
    }

    public String entitlementString(String key) {
        Object value = entitlement == null ? null : entitlement.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public Integer entitlementInteger(String key) {
        Object value = entitlement == null ? null : entitlement.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    public Long entitlementLong(String key) {
        Object value = entitlement == null ? null : entitlement.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    public BigDecimal entitlementDecimal(String key) {
        Object value = entitlement == null ? null : entitlement.get(key);
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    public PaymentProductSnapshot entitlement(String key, Object value) {
        entitlement.put(key, value);
        return this;
    }
}
