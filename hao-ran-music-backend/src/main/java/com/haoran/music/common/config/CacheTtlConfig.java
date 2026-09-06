package com.haoran.music.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

   
                                        
  
                      
   
@Data
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheTtlConfig {

    private Expire mv = new Expire(1800L);
    private Expire song = new Expire(3600L);
    private Expire recommend = new Expire(1800L);
    private Expire hot = new Expire(86400L);
    private Expire nullValue = new Expire(300L);

    public Expire getMv() {
        if (mv == null) {
            mv = new Expire(1800L);
        }
        return mv;
    }

    public Expire getSong() {
        if (song == null) {
            song = new Expire(3600L);
        }
        return song;
    }

    public Expire getRecommend() {
        if (recommend == null) {
            recommend = new Expire(1800L);
        }
        return recommend;
    }

    public Expire getHot() {
        if (hot == null) {
            hot = new Expire(86400L);
        }
        return hot;
    }

    public Expire getNullValue() {
        if (nullValue == null) {
            nullValue = new Expire(300L);
        }
        return nullValue;
    }

    @Data
    public static class Expire {
        private long expire;

        public Expire() {
        }

        public Expire(long expire) {
            this.expire = expire;
        }
    }
}
