   
                      
                       
   
package com.haoran.music.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("payment_config")
public class PaymentConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String userType;

    private String paymentType;

       
               
       
    private String qrCodeUrl;

       
                    
       
    private String qrCodeWithVerify;

       
          
       
    private String verifyCode;

       
              
       
    private String md5Hash;

       
                     
       
    private Integer isEnabled;

       
             
       
    private BigDecimal dailyLimit;

       
              
       
    private BigDecimal todayReceived;

       
             
       
    private LocalDate lastResetDate;

       
             
       
    private LocalDateTime lastScanTime;

       
           
       
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

       
           
       
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

                          
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }

    public String getQrCodeWithVerify() { return qrCodeWithVerify; }
    public void setQrCodeWithVerify(String qrCodeWithVerify) { this.qrCodeWithVerify = qrCodeWithVerify; }

    public String getVerifyCode() { return verifyCode; }
    public void setVerifyCode(String verifyCode) { this.verifyCode = verifyCode; }

    public String getMd5Hash() { return md5Hash; }
    public void setMd5Hash(String md5Hash) { this.md5Hash = md5Hash; }

    public Integer getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Integer isEnabled) { this.isEnabled = isEnabled; }

    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }

    public BigDecimal getTodayReceived() { return todayReceived; }
    public void setTodayReceived(BigDecimal todayReceived) { this.todayReceived = todayReceived; }

    public LocalDate getLastResetDate() { return lastResetDate; }
    public void setLastResetDate(LocalDate lastResetDate) { this.lastResetDate = lastResetDate; }

    public LocalDateTime getLastScanTime() { return lastScanTime; }
    public void setLastScanTime(LocalDateTime lastScanTime) { this.lastScanTime = lastScanTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
