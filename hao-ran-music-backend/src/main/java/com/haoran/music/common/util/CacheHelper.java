package com.haoran.music.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;







public class CacheHelper {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CacheHelper.class);
    private static final long DEFAULT_SCAN_COUNT = 1000L;
    private static final Map<String, Object> LOAD_LOCKS = new ConcurrentHashMap<>();

    private CacheHelper() {

    }














    public static <T> T getOrLoad(RedisUtils redisUtils,
                                  String cacheKey,
                                  Supplier<T> supplier,
                                  long expire,
                                  TimeUnit timeUnit,
                                  Class<T> clazz) {

        T cached = castCached(redisUtils.get(cacheKey), cacheKey, clazz);
        if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(cached)) {
            return cached;
        }

        Object loadLock = LOAD_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        try {
            synchronized (loadLock) {
                T cachedAfterLock = castCached(redisUtils.get(cacheKey), cacheKey, clazz);
                if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(cachedAfterLock)) {
                    return cachedAfterLock;
                }
                T data = supplier.get();
                if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(data)) {
                    setWithRandomTTL(redisUtils, cacheKey, data, expire, timeUnit);
                }
                return data;
            }
        } finally {
            LOAD_LOCKS.remove(cacheKey, loadLock);
        }
    }















    public static <T> T getOrLoadWithNullProtection(RedisUtils redisUtils,
                                                     String cacheKey,
                                                     Supplier<T> supplier,
                                                     long expire,
                                                     long nullExpire,
                                                     TimeUnit timeUnit,
                                                     Class<T> clazz) {

        Object cached = redisUtils.get(cacheKey);
        if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(cached)) {

            if (cached instanceof NullMarker) {
                return null;
            }
            T typedCached = castCached(cached, cacheKey, clazz);
            if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(typedCached)) {
                return typedCached;
            }
        }

        Object loadLock = LOAD_LOCKS.computeIfAbsent(cacheKey, ignored -> new Object());
        try {
            synchronized (loadLock) {
                Object cachedAfterLock = redisUtils.get(cacheKey);
                if (cachedAfterLock instanceof NullMarker) {
                    return null;
                }
                T typedCached = castCached(cachedAfterLock, cacheKey, clazz);
                if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(typedCached)) {
                    return typedCached;
                }

                T data = supplier.get();
                if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(data)) {
                    setWithRandomTTL(redisUtils, cacheKey, data, expire, timeUnit);
                } else {
                    redisUtils.set(cacheKey, new NullMarker(), Math.max(1, nullExpire), timeUnit);
                }
                return data;
            }
        } finally {
            LOAD_LOCKS.remove(cacheKey, loadLock);
        }
    }

    private static <T> T castCached(Object cached, String cacheKey, Class<T> clazz) {
        if (com.haoran.music.common.util.ObjectUtils.isEmpty(cached) || cached instanceof NullMarker) {
            return null;
        }
        if (clazz.isInstance(cached)) {
            return clazz.cast(cached);
        }
        if (cached instanceof Number) {
            Number number = (Number) cached;
            Object converted = null;
            if (clazz == Long.class) {
                converted = number.longValue();
            } else if (clazz == Integer.class) {
                converted = number.intValue();
            } else if (clazz == Double.class) {
                converted = number.doubleValue();
            } else if (clazz == Float.class) {
                converted = number.floatValue();
            } else if (clazz == Short.class) {
                converted = number.shortValue();
            } else if (clazz == Byte.class) {
                converted = number.byteValue();
            }
            if (converted != null) {
                return clazz.cast(converted);
            }
        }
        log.warn("缓存类型转换失败: key={}, expected={}, actual={}",
                cacheKey, clazz.getName(), cached.getClass().getName());
        return null;
    }








    public static Set<String> keys(RedisUtils redisUtils, String pattern) {
        return redisUtils.keys(pattern);
    }




    public static Set<String> keys(RedisTemplate<String, Object> redisTemplate, String pattern) {
        if (redisTemplate == null || pattern == null || pattern.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new LinkedHashSet<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(pattern)
                    .count(DEFAULT_SCAN_COUNT)
                    .build());
            try {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            } finally {
                try {
                    cursor.close();
                } catch (Exception ignored) {

                }
            }
            return keys;
        });
        return result == null ? Collections.emptySet() : result;
    }




    public static Map<String, Long> countKeyPrefixes(RedisTemplate<String, Object> redisTemplate,
                                                     String pattern) {
        if (redisTemplate == null || pattern == null || pattern.trim().isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> result = redisTemplate.execute((RedisCallback<Map<String, Long>>) connection -> {
            Map<String, Long> counts = new HashMap<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(pattern)
                    .count(DEFAULT_SCAN_COUNT)
                    .build());
            try {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    int separator = key.indexOf(':');
                    String prefix = separator > 0 ? key.substring(0, separator) : key;
                    counts.put(prefix, counts.getOrDefault(prefix, 0L) + 1L);
                }
            } finally {
                try {
                    cursor.close();
                } catch (Exception ignored) {

                }
            }
            return counts;
        });
        return result == null ? Collections.emptyMap() : result;
    }








    public static long deleteByPattern(RedisUtils redisUtils, String pattern) {
        return redisUtils == null ? 0L : redisUtils.deleteByPattern(pattern);
    }

    public static long deleteByPattern(RedisTemplate<String, Object> redisTemplate, String pattern) {
        if (redisTemplate == null || pattern == null || pattern.trim().isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.execute((RedisCallback<Long>) connection -> {
            long total = 0L;
            Set<byte[]> batch = new LinkedHashSet<>(200);
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                    .match(pattern)
                    .count(DEFAULT_SCAN_COUNT)
                    .build());
            try {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= 200) {
                        total += deleteBatch(connection, batch);
                        batch.clear();
                    }
                }
                total += deleteBatch(connection, batch);
            } finally {
                try {
                    cursor.close();
                } catch (Exception ignored) {

                }
            }
            return total;
        });
        return deleted == null ? 0L : deleted;
    }

    private static long deleteBatch(org.springframework.data.redis.connection.RedisConnection connection,
                                    Set<byte[]> batch) {
        if (batch.isEmpty()) {
            return 0L;
        }
        Long deleted = connection.del(batch.toArray(new byte[0][]));
        return deleted == null ? 0L : deleted;
    }











    public static void setWithRandomTTL(RedisUtils redisUtils,
                                         String key,
                                         Object value,
                                         long baseExpire,
                                         TimeUnit timeUnit) {

        long randomTTL = baseExpire + (long) (Math.random() * baseExpire * 0.2 - baseExpire * 0.1);
        redisUtils.set(key, value, Math.max(1, randomTTL), timeUnit);
    }





    private static class NullMarker {

    }












    public static <T> void warmUp(RedisUtils redisUtils,
                                   String cacheKey,
                                   Supplier<T> supplier,
                                   long expire,
                                   TimeUnit timeUnit) {
        try {
            T data = supplier.get();
            if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(data)) {
                redisUtils.set(cacheKey, data, expire, timeUnit);
                log.info("缓存预热成功: key={}", cacheKey);
            }
        } catch (Exception e) {
            log.error("缓存预热失败: key={}", cacheKey);
        }
    }








    public static long getExpire(RedisUtils redisUtils, String key) {
        return redisUtils.getExpire(key);
    }








    public static boolean exists(RedisUtils redisUtils, String key) {
        return redisUtils.hasKey(key);
    }










    public static boolean expire(RedisUtils redisUtils, String key, long expire, TimeUnit timeUnit) {
        return redisUtils.expire(key, expire, timeUnit);
    }







    public static void delete(RedisUtils redisUtils, Set<String> keys) {
        if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(keys)) {
            redisUtils.delete(keys);
        }
    }







    public static void delete(RedisUtils redisUtils, String... keys) {
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                redisUtils.delete(key);
            }
        }
    }









    public static long increment(RedisUtils redisUtils, String key, long delta) {
        return redisUtils.increment(key, delta);
    }









    public static long decrement(RedisUtils redisUtils, String key, long delta) {
        return redisUtils.decrement(key, delta);
    }








    public static long getCounter(RedisUtils redisUtils, String key) {
        Object value = redisUtils.get(key);
        if (com.haoran.music.common.util.ObjectUtils.isNotEmpty(value)) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
