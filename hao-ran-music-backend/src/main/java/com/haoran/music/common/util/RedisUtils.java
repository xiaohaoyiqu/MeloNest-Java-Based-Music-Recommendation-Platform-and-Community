package com.haoran.music.common.util;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

   
                      
                        
   
@Component
public class RedisUtils {

    private static final long DEFAULT_SCAN_COUNT = 1000L;
    private static final DefaultRedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end " +
                    "redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2]) return 1",
            Long.class);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end " +
                    "return redis.call('DEL', KEYS[1])",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

                                                                        

       
                
      
                   
                                
       
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

       
            
      
                   
       
    public void delete(String key) {
        redisTemplate.delete(key);
    }

       
              
      
                      
       
    public void delete(Collection<String> keys) {
        redisTemplate.delete(keys);
    }

       
             
      
                       
                          
                          
                               
       
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

       
                 
      
                    
                       
       
    public void expireAt(String key, Date date) {
        redisTemplate.expireAt(key, date);
    }

       
             
      
                   
                      
       
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

                                                                           

       
            
      
                   
                
       
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

       
            
      
                     
                     
       
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

       
                   
      
                       
                       
                          
                          
       
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

       
                                                                                               
       
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        if (key == null || value == null || timeout <= 0 || unit == null) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

       
                                                                                          
       
    public boolean compareAndSet(String key, Object expectedValue, Object newValue,
                                 long timeout, TimeUnit unit) {
        if (key == null || expectedValue == null || newValue == null || unit == null || timeout <= 0) {
            return false;
        }
        Long result = redisTemplate.execute(COMPARE_AND_SET_SCRIPT, Collections.singletonList(key),
                expectedValue, newValue, unit.toMillis(timeout));
        return Long.valueOf(1L).equals(result);
    }

                                                                                   
    public boolean compareAndDelete(String key, Object expectedValue) {
        if (key == null || expectedValue == null) {
            return false;
        }
        Long result = redisTemplate.execute(COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(result);
    }

       
         
      
                   
                    
       
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

       
            
      
                     
                      
                    
       
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

       
         
      
                   
                    
       
    public Long decrement(String key) {
        return redisTemplate.opsForValue().decrement(key);
    }

       
            
      
                     
                      
                    
       
    public Long decrement(String key, long delta) {
        return redisTemplate.opsForValue().decrement(key, delta);
    }

                                                                         

       
                
      
                       
                           
                
       
    public Object hGet(String key, String hashKey) {
        return redisTemplate.opsForHash().get(key, hashKey);
    }

       
                
      
                       
                           
                       
       
    public void hSet(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

       
                
      
                        
                              
       
    public void hDelete(String key, Object... hashKeys) {
        redisTemplate.opsForHash().delete(key, hashKeys);
    }

       
             
      
                       
                           
                         
                    
       
    public long hIncrBy(String key, String hashKey, long delta) {
        return redisTemplate.opsForHash().increment(key, hashKey, delta);
    }

       
                          
      
                       
                           
                                
       
    public boolean hHasKey(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

                                                                        

       
               
      
                      
                        
                      
       
    public Long sAdd(String key, Object... values) {
        return redisTemplate.opsForSet().add(key, values);
    }

       
                 
      
                   
                  
       
    public Set<Object> sMembers(String key) {
        return redisTemplate.opsForSet().members(key);
    }

       
                   
      
                     
                     
                                
       
    public Boolean sIsMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

       
               
      
                   
                 
       
    public Long sSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }

       
               
      
                      
                        
                      
       
    public Long sRemove(String key, Object... values) {
        return redisTemplate.opsForSet().remove(key, values);
    }

                                                                         

       
                
      
                     
                     
                      
                      
       
    public Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

       
                           
      
                     
                        
                        
                  
       
    public Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

       
                           
      
                     
                        
                        
                  
       
    public Set<Object> zReverseRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

       
                  
      
                     
                     
                 
       
    public Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

       
                
      
                      
                        
                      
       
    public Long zRemove(String key, Object... values) {
        return redisTemplate.opsForZSet().remove(key, values);
    }

                                                                       

       
                 
      
                          
                  
       
    public Set<String> hKeys(String pattern) {
        return keys(pattern);
    }

       
                       
      
                          
                  
       
    public Set<String> keys(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
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

       
                                                              
      
                                 
                                     
       
    public long deleteByPattern(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.execute((RedisCallback<Long>) connection -> {
            long total = 0L;
            List<byte[]> batch = new ArrayList<>(200);
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

    private long deleteBatch(org.springframework.data.redis.connection.RedisConnection connection,
                             List<byte[]> batch) {
        if (batch.isEmpty()) {
            return 0L;
        }
        Long deleted = connection.del(batch.toArray(new byte[0][]));
        return deleted == null ? 0L : deleted;
    }
}
