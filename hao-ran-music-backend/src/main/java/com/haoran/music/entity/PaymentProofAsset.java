package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;






@Data
@TableName("payment_proof_asset")
public class PaymentProofAsset {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CLEANUP_PENDING = "cleanup_pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_CLEANED = "cleaned";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventId;
    private Long orderId;
    private Long ownerId;
    private String proofReference;
    private String referenceDigest;
    private String status;
    private Integer attemptCount;
    private Integer maxAttempts;
    private String workerId;
    private LocalDateTime leaseUntil;
    private String errorCategory;
    private LocalDateTime nextRetryTime;
    private LocalDateTime cleanedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
