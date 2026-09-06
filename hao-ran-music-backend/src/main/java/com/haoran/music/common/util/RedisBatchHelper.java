package com.haoran.music.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;







@Slf4j
public final class RedisBatchHelper {

    private RedisBatchHelper() {

    }










    public static Set<Long> getSetMemberIds(RedisUtils redisUtils, String pattern, Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptySet();
        }

        try {
            String key = pattern.replace("{userId}", String.valueOf(userId));
            Set<Object> members = redisUtils.sMembers(key);

            if (ObjectUtils.isEmpty(members)) {
                return Collections.emptySet();
            }

            Set<Long> result = new HashSet<>();
            for (Object member : members) {
                try {
                    result.add(Long.valueOf(member.toString()));
                } catch (NumberFormatException e) {
                    log.warn("无效的ID: {}", member);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("获取Set成员失败: pattern={}, userId={}", pattern, userId);
            return Collections.emptySet();
        }
    }









    public static Map<Long, Set<Long>> getSetMemberIdsBatch(RedisUtils redisUtils,
                                                              String pattern,
                                                              List<Long> userIds) {
        if (ObjectUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }

        Map<Long, Set<Long>> result = new HashMap<>();
        for (Long userId : userIds) {
            Set<Long> memberIds = getSetMemberIds(redisUtils, pattern, userId);
            result.put(userId, memberIds);
        }
        return result;
    }









    public static Set<Long> getMembersInSet(RedisUtils redisUtils, String key, List<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return Collections.emptySet();
        }

        try {
            Set<Object> allMembers = redisUtils.sMembers(key);
            if (ObjectUtils.isEmpty(allMembers)) {
                return Collections.emptySet();
            }


            Set<Long> memberSet = new HashSet<>();
            for (Object member : allMembers) {
                try {
                    memberSet.add(Long.valueOf(member.toString()));
                } catch (NumberFormatException e) {

                }
            }


            return ids.stream()
                    .filter(memberSet::contains)
                    .collect(Collectors.toSet());

        } catch (Exception e) {
            log.warn("检查Set成员失败: key={}", key);
            return Collections.emptySet();
        }
    }









    public static Map<String, Object> hGetBatch(RedisUtils redisUtils, String key, Set<String> fields) {
        if (ObjectUtils.isEmpty(fields)) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        for (String field : fields) {
            Object value = redisUtils.hGet(key, field);
            if (ObjectUtils.isNotEmpty(value)) {
                result.put(field, value);
            }
        }
        return result;
    }








    public static Map<String, Object> mGetBatch(RedisUtils redisUtils, List<String> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        for (String key : keys) {
            Object value = redisUtils.get(key);
            if (ObjectUtils.isNotEmpty(value)) {
                result.put(key, value);
            }
        }
        return result;
    }









    public static long sAddBatch(RedisUtils redisUtils, String key, List<Long> members) {
        if (ObjectUtils.isEmpty(members)) {
            return 0;
        }

        Object[] memberArray = members.stream()
                .map(String::valueOf)
                .toArray();

        return redisUtils.sAdd(key, memberArray);
    }









    public static long sRemoveBatch(RedisUtils redisUtils, String key, List<Long> members) {
        if (ObjectUtils.isEmpty(members)) {
            return 0;
        }

        Object[] memberArray = members.stream()
                .map(String::valueOf)
                .toArray();

        return redisUtils.sRemove(key, memberArray);
    }








    public static void hSetBatch(RedisUtils redisUtils, String key, Map<String, Object> map) {
        if (ObjectUtils.isEmpty(map)) {
            return;
        }

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            redisUtils.hSet(key, entry.getKey(), entry.getValue());
        }
    }









    public static Map<Long, Boolean> sIsMemberBatch(RedisUtils redisUtils, String key, Set<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }


        Set<Object> allMembers = redisUtils.sMembers(key);
        Set<Long> memberSet = new HashSet<>();

        if (ObjectUtils.isNotEmpty(allMembers)) {
            for (Object member : allMembers) {
                try {
                    memberSet.add(Long.valueOf(member.toString()));
                } catch (NumberFormatException e) {

                }
            }
        }


        Map<Long, Boolean> result = new HashMap<>();
        for (Long id : ids) {
            result.put(id, memberSet.contains(id));
        }
        return result;
    }









    public static void mSetBatch(RedisUtils redisUtils,
                                  Map<String, Object> data,
                                  long expire,
                                  TimeUnit timeUnit) {
        if (ObjectUtils.isEmpty(data)) {
            return;
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            redisUtils.set(entry.getKey(), entry.getValue(), expire, timeUnit);
        }
    }








    public static long deleteByPattern(RedisUtils redisUtils, String pattern) {
        return redisUtils == null ? 0L : redisUtils.deleteByPattern(pattern);
    }








    public static long deleteKeys(RedisUtils redisUtils, Set<String> keys) {
        if (ObjectUtils.isEmpty(keys)) {
            return 0;
        }
        redisUtils.delete(keys);
        return keys.size();
    }








    public static Set<String> keys(RedisUtils redisUtils, String pattern) {
        return redisUtils.keys(pattern);
    }











    public static <V> void setBatchStatus(List<V> voList,
                                            Function<V, Long> idExtractor,
                                            Set<Long> statusSet,
                                            BiConsumer<V, Boolean> statusSetter) {
        if (ObjectUtils.isEmpty(voList)) {
            return;
        }

        if (ObjectUtils.isEmpty(statusSet)) {

            for (V vo : voList) {
                statusSetter.accept(vo, false);
            }
        } else {

            for (V vo : voList) {
                Long id = idExtractor.apply(vo);
                statusSetter.accept(vo, statusSet.contains(id));
            }
        }
    }









    public static Map<Long, Long> getCounterBatch(RedisUtils redisUtils, String pattern, List<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            return Collections.emptyMap();
        }

        Map<Long, Long> result = new HashMap<>();
        for (Long id : ids) {
            String key = pattern.replace("{id}", String.valueOf(id));
            Object value = redisUtils.get(key);
            if (ObjectUtils.isNotEmpty(value)) {
                try {
                    result.put(id, Long.parseLong(value.toString()));
                } catch (NumberFormatException e) {
                    result.put(id, 0L);
                }
            } else {
                result.put(id, 0L);
            }
        }
        return result;
    }
}
