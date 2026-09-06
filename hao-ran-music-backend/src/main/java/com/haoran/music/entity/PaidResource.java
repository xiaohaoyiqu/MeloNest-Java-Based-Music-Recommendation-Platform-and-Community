package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;






@TableName("music_paid_resource")
public class PaidResource extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String resourceType;
    private Long resourceId;
    private Long ownerId;
    private String ownerType;
    private BigDecimal price;
    private String priceType;
    private Integer isEnabled;
    private String status;
    private Integer salesCount;
    private BigDecimal totalEarnings;
    private Integer subscribePeriod;
    private BigDecimal platformFeeRate;
    private String changeType;
    private String changeReason;
    private BigDecimal candidatePrice;
    private Integer candidateSubscribePeriod;
    private String candidateChangeType;
    private String candidateChangeReason;
    private String changeReviewStatus;
    private LocalDateTime candidateSubmittedAt;
    private Long candidateReviewerId;
    private LocalDateTime candidateReviewTime;
    private String candidateReviewReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public String getOwnerType() { return ownerType; }
    public void setOwnerType(String ownerType) { this.ownerType = ownerType; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getPriceType() { return priceType; }
    public void setPriceType(String priceType) { this.priceType = priceType; }

    public Integer getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Integer isEnabled) { this.isEnabled = isEnabled; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSalesCount() { return salesCount; }
    public void setSalesCount(Integer salesCount) { this.salesCount = salesCount; }

    public BigDecimal getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(BigDecimal totalEarnings) { this.totalEarnings = totalEarnings; }

    public Integer getSubscribePeriod() { return subscribePeriod; }
    public void setSubscribePeriod(Integer subscribePeriod) { this.subscribePeriod = subscribePeriod; }

    public BigDecimal getPlatformFeeRate() { return platformFeeRate; }
    public void setPlatformFeeRate(BigDecimal platformFeeRate) { this.platformFeeRate = platformFeeRate; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }

    public BigDecimal getCandidatePrice() { return candidatePrice; }
    public void setCandidatePrice(BigDecimal candidatePrice) { this.candidatePrice = candidatePrice; }

    public Integer getCandidateSubscribePeriod() { return candidateSubscribePeriod; }
    public void setCandidateSubscribePeriod(Integer candidateSubscribePeriod) {
        this.candidateSubscribePeriod = candidateSubscribePeriod;
    }

    public String getCandidateChangeType() { return candidateChangeType; }
    public void setCandidateChangeType(String candidateChangeType) { this.candidateChangeType = candidateChangeType; }

    public String getCandidateChangeReason() { return candidateChangeReason; }
    public void setCandidateChangeReason(String candidateChangeReason) { this.candidateChangeReason = candidateChangeReason; }

    public String getChangeReviewStatus() { return changeReviewStatus; }
    public void setChangeReviewStatus(String changeReviewStatus) { this.changeReviewStatus = changeReviewStatus; }

    public LocalDateTime getCandidateSubmittedAt() { return candidateSubmittedAt; }
    public void setCandidateSubmittedAt(LocalDateTime candidateSubmittedAt) {
        this.candidateSubmittedAt = candidateSubmittedAt;
    }

    public Long getCandidateReviewerId() { return candidateReviewerId; }
    public void setCandidateReviewerId(Long candidateReviewerId) { this.candidateReviewerId = candidateReviewerId; }

    public LocalDateTime getCandidateReviewTime() { return candidateReviewTime; }
    public void setCandidateReviewTime(LocalDateTime candidateReviewTime) {
        this.candidateReviewTime = candidateReviewTime;
    }

    public String getCandidateReviewReason() { return candidateReviewReason; }
    public void setCandidateReviewReason(String candidateReviewReason) {
        this.candidateReviewReason = candidateReviewReason;
    }
}
