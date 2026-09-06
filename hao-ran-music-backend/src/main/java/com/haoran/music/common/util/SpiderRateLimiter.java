package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;





@Slf4j
@Component
public class SpiderRateLimiter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpiderRateLimiter.class);

    private static final int DAILY_LIMIT = 2000;

    @Autowired
    private RedisUtils redisUtils;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");






    public boolean checkRateLimit() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = "spider:count:" + today;

        try {
            Object countObj = redisUtils.get(key);
            int currentCount = 0;
            if (countObj != null) {
                currentCount = Integer.parseInt(countObj.toString());
            }

            if (currentCount >= DAILY_LIMIT) {
                log.warn("[SpiderRateLimiter] 今日爬取次数已达上限: {}", currentCount);
                return false;
            }


            Long newCount = redisUtils.increment(key);

            if (newCount == 1) {
                redisUtils.expire(key, 24, TimeUnit.HOURS);
            }

            log.debug("[SpiderRateLimiter] 爬取计数更新: date={}, count={}", today, newCount);
            return true;

        } catch (Exception e) {
            log.error("[SpiderRateLimiter] 检查限流失败");

            return true;
        }
    }






    public int getRemainingCount() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = "spider:count:" + today;

        try {
            Object countObj = redisUtils.get(key);
            int currentCount = 0;
            if (countObj != null) {
                currentCount = Integer.parseInt(countObj.toString());
            }
            return Math.max(0, DAILY_LIMIT - currentCount);
        } catch (Exception e) {
            log.error("[SpiderRateLimiter] 获取剩余次数失败");
            return DAILY_LIMIT;
        }
    }






    public int getCurrentCount() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = "spider:count:" + today;

        try {
            Object countObj = redisUtils.get(key);
            if (countObj != null) {
                return Integer.parseInt(countObj.toString());
            }
            return 0;
        } catch (Exception e) {
            log.error("[SpiderRateLimiter] 获取当前次数失败");
            return 0;
        }
    }






    public boolean resetTodayCount() {
        String today = LocalDate.now().format(DATE_FORMATTER);
        String key = "spider:_count:" + today;

        try {
            redisUtils.delete(key);
            log.info("[SpiderLimiter] 重置今日爬取计数: date={}", today);
            return true;
        } catch (Exception e) {
            log.error("[SpiderLimiter] 重置计数失败");
            return false;
        }
    }
}

