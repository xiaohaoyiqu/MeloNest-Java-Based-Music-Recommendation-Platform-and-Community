   
                      
                       
   
package com.haoran.music.common.enums;

import java.time.Duration;

   
          
   
public enum VipLevel {

       
           
       
    FREE(0, "免费用户", 0, null, 10, 10),

       
            
       
    MONTHLY_VIP(1, "月度VIP", 10, Duration.ofDays(30), 20, 20),

       
            
       
    QUARTERLY_VIP(2, "季度VIP", 25, Duration.ofDays(90), 40, 50),

       
            
       
    YEARLY_VIP(3, "年度VIP", 100, Duration.ofDays(365), 70, 100);

       
           
       
    private final Integer code;

       
           
       
    private final String name;

       
            
       
    private final Integer price;

       
          
       
    private final Duration duration;

       
             
       
    private final Integer dailyDownloadQuota;

       
                
       
    private final Integer maxPlaylists;

    VipLevel(Integer code, String name, Integer price, Duration duration, Integer dailyDownloadQuota, Integer maxPlaylists) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.duration = duration;
        this.dailyDownloadQuota = dailyDownloadQuota;
        this.maxPlaylists = maxPlaylists;
    }

    public Integer getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getPrice() {
        return price;
    }

    public Duration getDuration() {
        return duration;
    }

    public Integer getDailyDownloadQuota() {
        return dailyDownloadQuota;
    }

    public Integer getMaxPlaylists() {
        return maxPlaylists;
    }

       
               
                  
                      
       
    public static VipLevel fromCode(Integer code) {
        if (code == null) {
            return FREE;
        }
        for (VipLevel level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return FREE;
    }

       
               
                          
       
    public boolean isPaidVip() {
        return this != FREE;
    }

       
                  
                       
                     
       
    public static int getDownloadQuotaByLevel(Integer levelCode) {
        VipLevel level = fromCode(levelCode);
        return level.getDailyDownloadQuota();
    }

       
                      
                       
                      
       
    public static int getMaxPlaylistsByLevel(Integer levelCode) {
        VipLevel level = fromCode(levelCode);
        return level.getMaxPlaylists();
    }

       
                
                 
       
    public String getDescription() {
        switch (this) {
            case MONTHLY_VIP:
                return "享受30天VIP特权，包含无损音质、高速下载等权益，可创建20个歌单";
            case QUARTERLY_VIP:
                return "享受90天VIP特权，相比月度更优惠，可创建50个歌单";
            case YEARLY_VIP:
                return "享受365天VIP特权，超值优惠，可创建100个歌单";
            case FREE:
            default:
                return "免费用户，可升级VIP享受更多特权，可创建10个歌单";
        }
    }

       
                 
                  
       
    public String getPlaylistLimitDesc() {
        return "可创建" + maxPlaylists + "个歌单";
    }
}
