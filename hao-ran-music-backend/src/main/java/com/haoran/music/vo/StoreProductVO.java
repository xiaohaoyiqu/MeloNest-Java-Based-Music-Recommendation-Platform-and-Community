



package com.haoran.music.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Data
public class StoreProductVO {
    private String productType;
    private Long productId;
    private String name;
    private String coverUrl;
    private Long sellerId;
    private String purchaseMode;
    private Integer pointsPrice;
    private BigDecimal cashPrice;
    private Integer itemCount;
    private Integer itemLimit;
    private String reviewStatus;
    private String saleStatus;
    private String visibility;
    private String entitlementPolicy;
    private String settlementStatus;
    private String effectiveStatus;
    private String reason;
    private LocalDateTime updateTime;
}
