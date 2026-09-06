package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;





@TableName("playlist_subscribe")
public class PlaylistSubscribe extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long playlistId;
    private Long userId;
    private Long creatorId;
    private String subscribeType;



    @TableField(exist = false)
    private Integer days;




    @TableField("price")
    private BigDecimal amount;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;




    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("expire_time")
    private LocalDateTime endTime;

    private Integer autoRenew;
    private String status;
    private Long paymentOrderId;




    @TableLogic
    private Integer deleted;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPlaylistId() { return playlistId; }
    public void setPlaylistId(Long playlistId) { this.playlistId = playlistId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getSubscribeType() { return subscribeType; }
    public void setSubscribeType(String subscribeType) { this.subscribeType = subscribeType; }

    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Integer autoRenew) { this.autoRenew = autoRenew; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getPaymentOrderId() { return paymentOrderId; }
    public void setPaymentOrderId(Long paymentOrderId) { this.paymentOrderId = paymentOrderId; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
