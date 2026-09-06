package com.haoran.music.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.haoran.music.common.constant.CommonConstants;
import com.haoran.music.common.util.ObjectUtils;
import com.haoran.music.common.util.UserAccountStatusUtil;
import com.haoran.music.entity.*;
import com.haoran.music.mapper.*;
import com.haoran.music.service.TopicRecommendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

   
           
  
        
                  
                
               
            
  
                      
   
@Slf4j
@Service
public class TopicRecommendServiceImpl implements TopicRecommendService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final MusicTopicMapper musicTopicMapper;
    private final TopicFollowMapper topicFollowMapper;
    private final SongLikeMapper songLikeMapper;
    private final SongMapper songMapper;
    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;

                            
    private static final Map<String, List<String>> GENRE_TOPIC_MAPPING;

    static {
        Map<String, List<String>> mapping = new HashMap<>();
        mapping.put("Pop", Arrays.asList("#流行音乐", "#华语流行", "#欧美流行"));
        mapping.put("Rock", Arrays.asList("#摇滚音乐", "#独立摇滚", "#经典摇滚"));
        mapping.put("Folk", Arrays.asList("#民谣音乐", "#校园民谣", "#独立民谣"));
        mapping.put("Electronic", Arrays.asList("#电子音乐", "#EDM", "#House音乐"));
        mapping.put("HipHop", Arrays.asList("#嘻哈音乐", "#说唱", "#R&B"));
        mapping.put("Classical", Arrays.asList("#古典音乐", "#交响乐", "#钢琴曲"));
        mapping.put("Jazz", Arrays.asList("#爵士音乐", "#蓝调", "#轻音乐"));
        mapping.put("Country", Arrays.asList("#乡村音乐", "#民谣", "#原声带"));
        GENRE_TOPIC_MAPPING = Collections.unmodifiableMap(mapping);
    }

    public TopicRecommendServiceImpl(MusicTopicMapper musicTopicMapper,
                                    TopicFollowMapper topicFollowMapper,
                                    SongLikeMapper songLikeMapper,
                                    SongMapper songMapper,
                                    UserMapper userMapper,
                                    UserFollowMapper userFollowMapper) {
        this.musicTopicMapper = musicTopicMapper;
        this.topicFollowMapper = topicFollowMapper;
        this.songLikeMapper = songLikeMapper;
        this.songMapper = songMapper;
        this.userMapper = userMapper;
        this.userFollowMapper = userFollowMapper;
    }

    @Override
    public List<Map<String, Object>> getRecommendedTopics(Long userId, Integer limit) {
        int actualLimit = normalizeLimit(limit);

                         
        Set<String> preferredGenres = getUserPreferredGenres(userId);

                       
        Set<Long> friendTopicIds = getFriendFollowedTopicIds(userId);

                    
        List<MusicTopic> hotTopics = getHotTopics(actualLimit * 2);

                    
        List<Map<String, Object>> result = new ArrayList<>();
        Set<Long> addedTopicIds = new HashSet<>();

        Map<String, String> genreByTopicName = new LinkedHashMap<>();
        for (String genre : preferredGenres) {
            List<String> names = GENRE_TOPIC_MAPPING.get(genre);
            if (names != null) {
                for (String name : names) {
                    genreByTopicName.putIfAbsent(name, genre);
                }
            }
        }
        Map<String, MusicTopic> topicsByName = getTopicsByNames(genreByTopicName.keySet());

                               
        for (String genre : preferredGenres) {
            List<String> topicNames = GENRE_TOPIC_MAPPING.get(genre);
            if (topicNames != null) {
                for (String topicName : topicNames) {
                    MusicTopic topic = topicsByName.get(topicName);
                    if (topic != null && !addedTopicIds.contains(topic.getId())) {
                        Map<String, Object> topicVO = convertToTopicVO(topic, "基于你的喜好: " + genre);
                        result.add(topicVO);
                        addedTopicIds.add(topic.getId());
                        if (result.size() >= actualLimit) {
                            return result;
                        }
                    }
                }
            }
        }

                             
        Map<Long, MusicTopic> friendTopics = friendTopicIds.isEmpty()
                ? Collections.emptyMap()
                : musicTopicMapper.selectBatchIds(friendTopicIds).stream()
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.toMap(MusicTopic::getId, topic -> topic));
        for (Long topicId : friendTopicIds) {
            if (!addedTopicIds.contains(topicId)) {
                MusicTopic topic = friendTopics.get(topicId);
                if (topic != null) {
                    Map<String, Object> topicVO = convertToTopicVO(topic, "好友关注");
                    result.add(topicVO);
                    addedTopicIds.add(topicId);
                    if (result.size() >= actualLimit) {
                        return result;
                    }
                }
            }
        }

                          
        for (MusicTopic topic : hotTopics) {
            if (!addedTopicIds.contains(topic.getId())) {
                Map<String, Object> topicVO = convertToTopicVO(topic, "热门话题");
                result.add(topicVO);
                addedTopicIds.add(topic.getId());
                if (result.size() >= actualLimit) {
                    break;
                }
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getTopicsByGenre(Long userId, String genre, Integer limit) {
        int actualLimit = normalizeLimit(limit);

                       
        List<String> topicNames = GENRE_TOPIC_MAPPING.get(genre);
        if (topicNames == null || topicNames.isEmpty()) {
                                
            return getHotTopics(actualLimit).stream()
                    .map(topic -> convertToTopicVO(topic, "热门话题"))
                    .collect(java.util.stream.Collectors.toList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, MusicTopic> topicsByName = getTopicsByNames(new LinkedHashSet<>(topicNames));
        for (String topicName : topicNames) {
            MusicTopic topic = topicsByName.get(topicName);
            if (topic != null) {
                Map<String, Object> topicVO = convertToTopicVO(topic, genre + "音乐推荐");
                result.add(topicVO);
                if (result.size() >= actualLimit) {
                    break;
                }
            }
        }

                          
        if (result.size() < actualLimit) {
            List<MusicTopic> hotTopics = getHotTopics(actualLimit - result.size());
            for (MusicTopic topic : hotTopics) {
                if (!topicIdsInResult(result, topic.getId())) {
                    Map<String, Object> topicVO = convertToTopicVO(topic, "热门话题");
                    result.add(topicVO);
                }
            }
        }

        return result;
    }

       
                  
                         
       
    private Set<String> getUserPreferredGenres(Long userId) {
        Set<String> genres = new HashSet<>();

                        
        LambdaQueryWrapper<SongLike> likeWrapper = new LambdaQueryWrapper<>();
        likeWrapper.eq(SongLike::getUserId, userId)
                .eq(SongLike::getIsFavorite, 1)
                .eq(SongLike::getDeleted, CommonConstants.NOT_DELETED)
                .last("LIMIT 100");

        List<SongLike> songLikes = songLikeMapper.selectList(likeWrapper);

                      
        Map<String, Integer> genreCount = new HashMap<>();
        Set<Long> songIds = songLikes.stream()
                .map(SongLike::getSongId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!songIds.isEmpty()) {
            for (Song song : songMapper.selectBatchIds(songIds)) {
                if (song != null && ObjectUtils.isNotEmpty(song.getMainType())) {
                    genreCount.merge(song.getMainType(), 1, Integer::sum);
                }
            }
        }

                       
        List<Map.Entry<String, Integer>> sortedGenres = new ArrayList<>(genreCount.entrySet());
        sortedGenres.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        for (int i = 0; i < Math.min(3, sortedGenres.size()); i++) {
            genres.add(sortedGenres.get(i).getKey());
        }

                        
        if (genres.isEmpty()) {
            genres.add("Pop");
        }

        return genres;
    }

       
                    
                         
       
    private Set<Long> getFriendFollowedTopicIds(Long userId) {
        Set<Long> topicIds = new HashSet<>();

                    
        Set<Long> friendIds = getMutualFriends(userId);

        if (friendIds.isEmpty()) {
            return topicIds;
        }
        LambdaQueryWrapper<TopicFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TopicFollow::getUserId, friendIds);
        for (TopicFollow follow : topicFollowMapper.selectList(wrapper)) {
            topicIds.add(follow.getTopicId());
        }

        return topicIds;
    }

       
             
                  
       
    private List<MusicTopic> getHotTopics(Integer limit) {
        LambdaQueryWrapper<MusicTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MusicTopic::getIsDeleted, false)
                .or()
                .isNull(MusicTopic::getIsDeleted)
                .orderByDesc(MusicTopic::getFollowerCount)
                .orderByDesc(MusicTopic::getPostCount)
                .last("LIMIT " + Math.min(Math.max(1, limit), MAX_LIMIT * 2));

        return musicTopicMapper.selectList(wrapper);
    }

    private Map<String, MusicTopic> getTopicsByNames(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<MusicTopic> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MusicTopic::getName, names)
                .eq(MusicTopic::getIsDeleted, false);
        return musicTopicMapper.selectList(wrapper).stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(
                        MusicTopic::getName, topic -> topic, (first, ignored) -> first));
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

       
                
                   
       
    private Set<Long> getMutualFriends(Long userId) {
        Set<Long> friends = new HashSet<>();

                  
        Set<Long> following = getFollowingUsers(userId);

                  
        Set<Long> followers = getFollowers(userId);

                 
        friends.addAll(following);
        friends.retainAll(followers);

        return filterEligibleUserIds(friends);
    }

       
                  
       
    private Set<Long> getFollowingUsers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = new HashSet<>(userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFolloweeId)
                .collect(java.util.stream.Collectors.toList()));
        return filterEligibleUserIds(userIds);
    }

       
                
       
    private Set<Long> getFollowers(Long userId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFolloweeId, userId)
                .eq(UserFollow::getDeleted, CommonConstants.NOT_DELETED);

        Set<Long> userIds = new HashSet<>(userFollowMapper.selectList(wrapper).stream()
                .map(UserFollow::getFollowerId)
                .collect(java.util.stream.Collectors.toList()));
        return filterEligibleUserIds(userIds);
    }

       
              
                    
       
    private Map<String, Object> convertToTopicVO(MusicTopic topic, String reason) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("id", topic.getId());
        vo.put("name", topic.getName());
        vo.put("description", topic.getDescription());
        vo.put("cover", topic.getCover());
        vo.put("category", topic.getCategory());
        vo.put("postCount", topic.getPostCount() != null ? topic.getPostCount() : 0);
        vo.put("followerCount", topic.getFollowerCount() != null ? topic.getFollowerCount() : 0);
        vo.put("isHot", topic.getIsHot() != null && topic.getIsHot());
        vo.put("recommendReason", reason);
        return vo;
    }

    private Set<Long> filterEligibleUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> safeUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (safeUserIds.isEmpty()) {
            return Collections.emptySet();
        }

        return userMapper.selectBatchIds(safeUserIds).stream()
                .filter(UserAccountStatusUtil::canAppearInRecommendations)
                .map(User::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

       
                    
       
    private boolean topicIdsInResult(List<Map<String, Object>> result, Long topicId) {
        for (Map<String, Object> item : result) {
            if (topicId.equals(item.get("id"))) {
                return true;
            }
        }
        return false;
    }
}
